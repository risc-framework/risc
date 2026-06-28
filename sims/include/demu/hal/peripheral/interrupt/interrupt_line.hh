#pragma once

namespace demu::hal::peripheral {

class InterruptLine {
public:
  virtual ~InterruptLine() = default;

  virtual void assert_line() noexcept { level_ = true; }
  virtual void deassert_line() noexcept { level_ = false; }
  virtual void set_level(bool level) noexcept { level_ = level; }

  [[nodiscard]] virtual auto get_level() const noexcept -> bool {
    return level_;
  }

private:
  bool level_{false};
};

} // namespace demu::hal::peripheral
