// Reads files from an ISO 9660 disc image through a "read at offset" function, so it works on a file descriptor
// (the Android file picker gives one) and in tests on a memory buffer. Only the root directory is searched: the
// game's executable (SLES_525.68) and its boot file are there.
#pragma once

#include <cstddef>
#include <cstdint>
#include <functional>
#include <string>

using ReadAt = std::function<bool(uint64_t offset, void* buffer, size_t length)>;

struct IsoEntry {
    uint32_t lba = 0;   // first sector of the file
    uint32_t size = 0;  // size in bytes
};

// Finds `name` (as written on the disc, without the ";1" version) in the disc's root directory.
bool IsoFindRootFile(const ReadAt& read, const std::string& name, IsoEntry* out, std::string* error);
