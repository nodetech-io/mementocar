#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
# SPDX-License-Identifier: GPL-3.0-or-later
"""Check 64-bit ELF/RELRO and APK ZIP alignment using only Python's stdlib.

AAB checks cover ELF files only, not APK packaging. Check the APK set generated
by bundletool as well. This does not verify signatures or replace runtime tests.
"""

import argparse
import io
from pathlib import Path
import struct
import sys
import zipfile

PAGE_SIZE = 16384
PT_LOAD = 1
PT_GNU_RELRO = 0x6474E552
PF_W = 2


class AlignmentError(ValueError):
    pass


def check_elf(data):
    """Return True for a checked ELF64, False for ELF32; reject invalid input."""
    if len(data) < 16 or data[:4] != b"\x7fELF":
        raise AlignmentError("not an ELF file")
    if data[4] == 1:
        return False  # Android's 16 KB requirement concerns 64-bit ABIs.
    if data[4] != 2 or data[5] not in (1, 2):
        raise AlignmentError("unsupported ELF class or byte order")
    endian = "<" if data[5] == 1 else ">"
    if len(data) < 64:
        raise AlignmentError("truncated ELF64 header")
    header = struct.unpack_from(endian + "16sHHIQQQIHHHHHH", data)
    phoff, phsize, phnum = header[5], header[9], header[10]
    if phsize < 56 or phnum in (0, 0xFFFF) or phoff + phsize * phnum > len(data):
        raise AlignmentError("invalid or unsupported program header table")
    loads, relros = [], []
    for index in range(phnum):
        kind, flags, offset, address, _, filesz, memsz, align = struct.unpack_from(
            endian + "IIQQQQQQ", data, phoff + index * phsize,
        )
        if kind not in (PT_LOAD, PT_GNU_RELRO):
            continue
        if offset + filesz > len(data) or address + memsz > 2**64 or filesz > memsz:
            raise AlignmentError("invalid segment bounds")
        if kind == PT_LOAD:
            if align < PAGE_SIZE or align & (align - 1) or (offset - address) % align:
                raise AlignmentError("LOAD segment is not 16 KB aligned/congruent")
            loads.append((address, address + memsz, flags))
        elif memsz:
            relros.append((address, address + memsz))
    if not loads:
        raise AlignmentError("ELF64 has no LOAD segments")
    for start, end in relros:
        # Rounding RELRO protection to 16 KB must not make other writable data
        # read-only. An unaligned end is safe if its LOAD contains only RELRO and
        # the next writable LOAD starts on a separate page (e.g. AndroidX Path).
        protected_start = start // PAGE_SIZE * PAGE_SIZE
        protected_end = (end + PAGE_SIZE - 1) // PAGE_SIZE * PAGE_SIZE
        if not any(lo <= start < hi for lo, hi, _ in loads):
            raise AlignmentError("RELRO is outside LOAD segments")
        for lo, hi, flags in loads:
            if flags & PF_W and (
                max(lo, protected_start) < min(hi, start)
                or max(lo, end) < min(hi, protected_end)
            ):
                raise AlignmentError("16 KB RELRO protection overlaps writable data")
    return True


def check_zip_entry(archive, info):
    """Check stored APK entries like zipalign -c -P 16 4 (not AAB entries)."""
    if info.is_dir() or info.compress_type != zipfile.ZIP_STORED:
        return
    archive.fp.seek(info.header_offset)
    local = archive.fp.read(30)
    if len(local) != 30 or local[:4] != b"PK\x03\x04":
        raise AlignmentError("invalid ZIP local header")
    name_size, extra_size = struct.unpack_from("<HH", local, 26)
    data_offset = info.header_offset + 30 + name_size + extra_size
    alignment = PAGE_SIZE if info.filename.endswith(".so") else 4
    if data_offset % alignment:
        raise AlignmentError(f"ZIP data offset {data_offset} is not {alignment}-byte aligned")


def check_archive(archive, apk=False, label=""):
    names = archive.namelist()
    if len(names) != len(set(names)):
        raise AlignmentError(f"{label}: duplicate ZIP entry names")
    count = 0
    for info in archive.infolist():
        try:
            if apk:
                check_zip_entry(archive, info)
            if info.filename.endswith(".so"):
                checked = check_elf(archive.read(info))
                count += int(checked)
                print(f"{'PASS ELF64' if checked else 'SKIP ELF32'} {label}{info.filename}")
        except AlignmentError as error:
            raise AlignmentError(f"{label}{info.filename}: {error}") from error
    return count


def check_file(path):
    with zipfile.ZipFile(path) as archive:
        if path.suffix == ".apks":
            apks = [info for info in archive.infolist() if info.filename.endswith(".apk")]
            if not apks:
                raise AlignmentError("APK set contains no APKs")
            count = 0
            for info in apks:
                with zipfile.ZipFile(io.BytesIO(archive.read(info))) as apk:
                    count += check_archive(apk, apk=True, label=info.filename + ": ")
            print(f"PASS ZIP alignment: {len(apks)} APKs")
        else:
            count = check_archive(archive, apk=path.suffix == ".apk")
            if path.suffix == ".aab":
                print("NOTE: AAB ZIP offsets are not APK offsets; also check generated .apks")
            else:
                print("PASS ZIP alignment: APK")
        print(f"PASS: {count} ELF64 libraries checked (including repeated splits)")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path, help=".aab, .apk or bundletool .apks file")
    args = parser.parse_args()
    if args.archive.suffix not in (".aab", ".apk", ".apks"):
        parser.error("expected an .aab, .apk or .apks archive")
    try:
        check_file(args.archive)
    except (AlignmentError, OSError, zipfile.BadZipFile, RuntimeError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
