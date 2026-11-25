#include "iso9660.h"

#include <cstring>
#include <vector>

namespace {

constexpr uint32_t kSector = 2048;
constexpr uint32_t kVolumeDescriptorSector = 16;
constexpr size_t kRootRecordOffset = 156;   // inside the primary volume descriptor
constexpr uint32_t kMaxDirectory = 1u << 20;  // the root directory of a game disc is a few KB

uint32_t Le32(const uint8_t* p) {
    return static_cast<uint32_t>(p[0]) | static_cast<uint32_t>(p[1]) << 8 |
           static_cast<uint32_t>(p[2]) << 16 | static_cast<uint32_t>(p[3]) << 24;
}

// "SLES_525.68;1" -> "SLES_525.68"
std::string EntryName(const uint8_t* name, size_t length) {
    std::string s(reinterpret_cast<const char*>(name), length);
    const size_t semicolon = s.find(';');
    if (semicolon != std::string::npos) {
        s.resize(semicolon);
    }
    return s;
}

}  // namespace

bool IsoFindRootFile(const ReadAt& read, const std::string& name, IsoEntry* out, std::string* error) {
    uint8_t volume[kSector];
    if (!read(static_cast<uint64_t>(kVolumeDescriptorSector) * kSector, volume, kSector)) {
        *error = "cannot read the disc";
        return false;
    }
    if (volume[0] != 1 || std::memcmp(volume + 1, "CD001", 5) != 0) {
        *error = "not an ISO 9660 disc image";
        return false;
    }

    const uint8_t* root = volume + kRootRecordOffset;
    const uint32_t directoryLba = Le32(root + 2);
    const uint32_t directorySize = Le32(root + 10);
    if (directorySize == 0 || directorySize > kMaxDirectory) {
        *error = "the disc's root directory is damaged";
        return false;
    }

    std::vector<uint8_t> directory(directorySize);
    if (!read(static_cast<uint64_t>(directoryLba) * kSector, directory.data(), directorySize)) {
        *error = "cannot read the disc's root directory";
        return false;
    }

    // Directory records never cross a sector boundary: a zero length means the rest of the sector is padding.
    size_t pos = 0;
    while (pos < directory.size()) {
        const uint8_t length = directory[pos];
        if (length == 0) {
            pos = (pos / kSector + 1) * kSector;
            continue;
        }
        if (length < 33 || pos + length > directory.size()) {
            break;
        }
        const uint8_t* record = &directory[pos];
        const uint8_t nameLength = record[32];
        const uint8_t flags = record[25];
        const bool isDirectory = (flags & 0x02) != 0;
        if (!isDirectory && nameLength > 0 && EntryName(record + 33, nameLength) == name) {
            out->lba = Le32(record + 2);
            out->size = Le32(record + 10);
            return true;
        }
        pos += length;
    }

    *error = name + " is not in the disc's root directory";
    return false;
}
