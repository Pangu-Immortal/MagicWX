#ifndef MEMUTILS_HPP
#define MEMUTILS_HPP

#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

#include <cstddef>
#include <cstdint>
#include <functional>
#include <string>
#include <vector>

// FP16 token-embedding lookup table. Either owns a converted vector (when the
// on-disk data was FP32 and had to be narrowed) or maps the on-disk FP16 file
// read-only. Lookups are sparse (only the prompt's token rows), so the mmap
// path keeps the large table out of resident anonymous memory: untouched rows
// never fault in, and the pages that do are clean and reclaimable.
class TokenEmbTable {
 public:
  TokenEmbTable() = default;
  ~TokenEmbTable() { reset(); }
  TokenEmbTable(const TokenEmbTable &) = delete;
  TokenEmbTable &operator=(const TokenEmbTable &) = delete;

  bool empty() const { return data_ == nullptr; }
  uint16_t operator[](size_t i) const { return data_[i]; }

  void setOwned(std::vector<uint16_t> &&v) {
    reset();
    owned_ = std::move(v);
    data_ = owned_.data();
  }
  void setMapped(void *base, size_t bytes) {
    reset();
    map_ = base;
    mapBytes_ = bytes;
    data_ = static_cast<const uint16_t *>(base);
  }

 private:
  void reset() {
    if (map_ != nullptr) {
      munmap(map_, mapBytes_);
      map_ = nullptr;
      mapBytes_ = 0;
    }
    owned_ = std::vector<uint16_t>();
    data_ = nullptr;
  }
  const uint16_t *data_ = nullptr;
  std::vector<uint16_t> owned_;
  void *map_ = nullptr;
  size_t mapBytes_ = 0;
};

// RAII read-only whole-file memory map. For large, transient inputs (e.g. the
// original model used as a zstd patch dictionary) this keeps the bytes as
// reclaimable, file-backed pages instead of a large anonymous heap buffer, and
// unmaps on every scope exit including exceptions.
struct MmapFile {
  const uint8_t *data = nullptr;
  size_t size = 0;

  explicit MmapFile(const std::string &path) {
    int fd = open(path.c_str(), O_RDONLY);
    if (fd < 0) return;
    struct stat st{};
    if (0 == fstat(fd, &st) && st.st_size > 0) {
      void *m = mmap(nullptr, static_cast<size_t>(st.st_size), PROT_READ,
                     MAP_PRIVATE, fd, 0);
      if (m != MAP_FAILED) {
        base_ = m;
        size = static_cast<size_t>(st.st_size);
        data = static_cast<const uint8_t *>(m);
      }
    }
    close(fd);
  }
  ~MmapFile() {
    if (base_ != nullptr) munmap(base_, size);
  }
  MmapFile(const MmapFile &) = delete;
  MmapFile &operator=(const MmapFile &) = delete;
  bool valid() const { return data != nullptr; }

 private:
  void *base_ = nullptr;
};

// Generic RAII guard: invokes the stored callable on scope exit. Used to make
// sure lowram-loaded models are released even if a pipeline throws partway.
struct ScopeExit {
  std::function<void()> fn;
  ~ScopeExit() {
    if (fn) fn();
  }
};

#endif  // MEMUTILS_HPP
