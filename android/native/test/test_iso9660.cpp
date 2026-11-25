// Host test (no Android): builds a tiny ISO 9660 image in memory and checks the reader finds the executable.
#include "../src/iso9660.h"

#include <cstdio>
#include <cstring>
#include <vector>

static void PutLe32(uint8_t* p, uint32_t v) {
    p[0] = v; p[1] = v >> 8; p[2] = v >> 16; p[3] = v >> 24;
}

static void PutRecord(uint8_t* p, uint32_t lba, uint32_t size, uint8_t flags, const char* name) {
    const uint8_t nameLength = static_cast<uint8_t>(std::strlen(name));
    const uint8_t length = static_cast<uint8_t>(33 + nameLength + (nameLength % 2 == 0 ? 1 : 0));
    p[0] = length;
    PutLe32(p + 2, lba);
    PutLe32(p + 10, size);
    p[25] = flags;
    p[32] = nameLength;
    std::memcpy(p + 33, name, nameLength);
}

int main() {
    const uint32_t kSector = 2048;
    std::vector<uint8_t> disc(24 * kSector, 0);

    // Primary volume descriptor at sector 16, root record at offset 156 pointing at sector 18.
    uint8_t* pvd = &disc[16 * kSector];
    pvd[0] = 1;
    std::memcpy(pvd + 1, "CD001", 5);
    uint8_t* root = pvd + 156;
    root[0] = 34;
    PutLe32(root + 2, 18);
    PutLe32(root + 10, kSector);
    root[25] = 0x02;

    // Root directory at sector 18: ".", "..", a subdirectory, then the executable.
    uint8_t* dir = &disc[18 * kSector];
    PutRecord(dir, 18, kSector, 0x02, "\x00");
    PutRecord(dir + 34, 18, kSector, 0x02, "\x01");
    PutRecord(dir + 68, 19, kSector, 0x02, "SUBDIR");
    PutRecord(dir + 68 + 40, 20, 8, 0x00, "SLES_525.68;1");

    // The executable's first bytes at sector 20: an ELF header.
    const uint8_t elf[8] = {0x7f, 'E', 'L', 'F', 1, 1, 1, 0};
    std::memcpy(&disc[20 * kSector], elf, 8);

    const ReadAt read = [&](uint64_t offset, void* buffer, size_t length) {
        if (offset + length > disc.size()) return false;
        std::memcpy(buffer, disc.data() + offset, length);
        return true;
    };

    int failures = 0;
    IsoEntry entry;
    std::string error;

    if (IsoFindRootFile(read, "SLES_525.68", &entry, &error) && entry.lba == 20 && entry.size == 8) {
        std::printf("ok: found SLES_525.68 at sector %u, %u bytes\n", entry.lba, entry.size);
    } else {
        std::printf("FAIL: SLES_525.68 not found (%s)\n", error.c_str());
        failures++;
    }

    if (!IsoFindRootFile(read, "SLES_999.99", &entry, &error)) {
        std::printf("ok: missing file rejected (%s)\n", error.c_str());
    } else {
        std::printf("FAIL: a missing file was found\n");
        failures++;
    }

    std::vector<uint8_t> notIso(24 * kSector, 0x55);
    const ReadAt readJunk = [&](uint64_t offset, void* buffer, size_t length) {
        std::memcpy(buffer, notIso.data() + offset, length);
        return true;
    };
    if (!IsoFindRootFile(readJunk, "SLES_525.68", &entry, &error)) {
        std::printf("ok: not a disc rejected (%s)\n", error.c_str());
    } else {
        std::printf("FAIL: junk accepted as a disc\n");
        failures++;
    }

    std::printf(failures == 0 ? "all iso9660 tests passed\n" : "%d failure(s)\n", failures);
    return failures == 0 ? 0 : 1;
}
