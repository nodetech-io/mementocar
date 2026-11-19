# SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
# SPDX-License-Identifier: GPL-3.0-or-later

import contextlib
import io
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile

from check_16k import AlignmentError, check_archive, check_elf, check_file, check_zip_entry


def elf(segments=None, endian="<"):
    # Program headers: kind, flags, file offset, virtual address, file size, memory size, alignment.
    if segments is None:
        segments = [(1, 6, 0, 0, 0x8000, 0x8000, 0x4000)]
    size = max(64 + 56 * len(segments), *(offset + filesz for _, _, offset, _, filesz, _, _ in segments))
    data = bytearray(size)
    ident = b"\x7fELF" + bytes([2, 1 if endian == "<" else 2, 1]) + bytes(9)
    struct.pack_into(endian + "16sHHIQQQIHHHHHH", data, 0,
                     ident, 3, 62, 1, 0, 64, 0, 0, 64, 56, len(segments), 0, 0, 0)
    for index, (kind, flags, offset, address, filesz, memsz, align) in enumerate(segments):
        struct.pack_into(endian + "IIQQQQQQ", data, 64 + index * 56,
                         kind, flags, offset, address, address, filesz, memsz, align)
    return bytes(data)


def stored_zip(name, data, alignment):
    result = io.BytesIO()
    info = zipfile.ZipInfo(name)
    padding = -(30 + len(name.encode("utf-8"))) % alignment
    if 0 < padding < 4:
        padding += alignment
    if padding:
        info.extra = struct.pack("<HH", 0xFFFF, padding - 4) + bytes(padding - 4)
    with zipfile.ZipFile(result, "w") as archive:
        archive.writestr(info, data)
    return result.getvalue()


class ElfAlignmentTest(unittest.TestCase):
    def test_aligned_elf_in_both_byte_orders(self):
        self.assertTrue(check_elf(elf()))
        self.assertTrue(check_elf(elf(endian=">")))

    def test_32_bit_is_explicitly_out_of_scope(self):
        self.assertFalse(check_elf(b"\x7fELF\x01" + bytes(11)))

    def test_rejects_4k_and_non_power_of_two_load_alignment(self):
        for alignment in (4096, 24576):
            with self.subTest(alignment=alignment), self.assertRaises(AlignmentError):
                check_elf(elf([(1, 6, 0, 0, 4096, 4096, alignment)]))

    def test_rejects_incongruent_load_addresses(self):
        with self.assertRaisesRegex(AlignmentError, "congruent"):
            check_elf(elf([(1, 6, 0, 4096, 4096, 4096, 16384)]))

    def test_rejects_truncated_headers_and_missing_loads(self):
        for data in (b"not ELF", elf()[:40], elf()[:80], elf([(4, 0, 0, 0, 0, 0, 1)])):
            with self.subTest(size=len(data)), self.assertRaises(AlignmentError):
                check_elf(data)

    def test_aligned_relro_end_is_safe(self):
        self.assertTrue(check_elf(elf([
            (1, 6, 0, 0, 0x8000, 0x8000, 0x4000),
            (0x6474E552, 4, 0, 0, 0x4000, 0x4000, 1),
        ])))

    def test_rejects_relro_rounding_over_subsequent_writable_data(self):
        with self.assertRaisesRegex(AlignmentError, "RELRO protection"):
            check_elf(elf([
                (1, 6, 0, 0, 0x8000, 0x8000, 0x4000),
                (0x6474E552, 4, 0, 0, 0x1000, 0x1000, 1),
            ]))

    def test_accepts_unaligned_relro_end_with_isolated_load(self):
        # RELRO may extend beyond its LOAD as padding, as in AndroidX Path.
        self.assertTrue(check_elf(elf([
            (1, 5, 0, 0, 0x1000, 0x1000, 0x4000),
            (1, 6, 0x4000, 0x4000, 0x800, 0x800, 0x4000),
            (1, 6, 0x8000, 0x8000, 8, 8, 0x4000),
            (0x6474E552, 4, 0x4000, 0x4000, 0x800, 0x1000, 1),
        ])))

    def test_rejects_relro_outside_loads(self):
        with self.assertRaisesRegex(AlignmentError, "outside"):
            check_elf(elf([
                (1, 6, 0, 0, 0x1000, 0x1000, 0x4000),
                (0x6474E552, 4, 0, 0x100000, 16, 16, 1),
            ]))


class ArchiveAlignmentTest(unittest.TestCase):
    def test_stored_native_library_needs_16k_zip_alignment(self):
        for alignment in (16384, 4096):
            with zipfile.ZipFile(io.BytesIO(stored_zip("lib/x86_64/test.so", b"x", alignment))) as archive:
                if alignment == 16384:
                    check_zip_entry(archive, archive.infolist()[0])
                else:
                    with self.assertRaisesRegex(AlignmentError, "16384-byte"):
                        check_zip_entry(archive, archive.infolist()[0])

    def test_compressed_entries_do_not_need_zip_alignment(self):
        result = io.BytesIO()
        with zipfile.ZipFile(result, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            archive.writestr("lib/x86_64/test.so", b"x")
        with zipfile.ZipFile(result) as archive:
            check_zip_entry(archive, archive.infolist()[0])

    def test_aab_offsets_are_not_checked_as_apk_offsets(self):
        result = stored_zip("base/lib/x86_64/test.so", elf(), 4)
        with zipfile.ZipFile(io.BytesIO(result)) as archive, contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(1, check_archive(archive))
            with self.assertRaises(AlignmentError):
                check_archive(archive, apk=True)

    def test_checks_nested_apks_and_rejects_empty_apk_sets(self):
        with tempfile.TemporaryDirectory() as directory, contextlib.redirect_stdout(io.StringIO()):
            path = Path(directory) / "release.apks"
            for contents in (elf(), elf([(1, 6, 0, 0, 4096, 4096, 4096)])):
                with zipfile.ZipFile(path, "w") as archive:
                    archive.writestr("splits/base.apk", stored_zip("lib/x86_64/test.so", contents, 16384))
                if contents == elf():
                    check_file(path)
                else:
                    with self.assertRaises(AlignmentError):
                        check_file(path)
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("toc.pb", b"x")
            with self.assertRaisesRegex(AlignmentError, "no APKs"):
                check_file(path)


if __name__ == "__main__":
    unittest.main()
