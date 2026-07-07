#pragma once

#include <memory>
#include <spdlog/pattern_formatter.h>
#include <spdlog/sinks/stdout_color_sinks.h>
#include <spdlog/spdlog.h>

namespace demu {

class Formatter : public spdlog::custom_flag_formatter {
public:
  void format(const spdlog::details::log_msg &msg, const std::tm &,
              spdlog::memory_buf_t &dest) override {
    std::string_view level_name;
    switch (msg.level) {
    case spdlog::level::trace:
      level_name = " TRACE";
      break;
    case spdlog::level::debug:
      level_name = " DEBUG";
      break;
    case spdlog::level::info:
      level_name = " INFO ";
      break;
    case spdlog::level::warn:
      level_name = " WARN ";
      break;
    case spdlog::level::err:
      level_name = " ERROR";
      break;
    case spdlog::level::critical:
      level_name = " CRIT ";
      break;
    default:
      level_name = " UNKN ";
      break;
    }
    dest.append(level_name.data(), level_name.data() + level_name.size());
  }

  [[nodiscard]] auto clone() const
      -> std::unique_ptr<custom_flag_formatter> override {
    return std::make_unique<Formatter>();
  }
};

class Logger {
public:
  static void init();
  static void init(spdlog::level::level_enum level);

  static auto getDemuLogger() -> std::shared_ptr<spdlog::logger> & {
    return demu_logger_;
  }

  static auto getHalLogger() -> std::shared_ptr<spdlog::logger> & {
    return hal_logger_;
  }

  [[nodiscard]] static auto demu_should_log(spdlog::level::level_enum level)
      -> bool {
    return demu_logger_ && demu_logger_->should_log(level);
  }

  [[nodiscard]] static auto hal_should_log(spdlog::level::level_enum level)
      -> bool {
    return hal_logger_ && hal_logger_->should_log(level);
  }

private:
  static std::shared_ptr<spdlog::logger> demu_logger_;
  static std::shared_ptr<spdlog::logger> isa_logger_;
  static std::shared_ptr<spdlog::logger> hal_logger_;
};
} // namespace demu

// Logging Macros
#define DEMU_TRACE(...) ::demu::Logger::getDemuLogger()->trace(__VA_ARGS__);
#define DEMU_DEBUG(...) ::demu::Logger::getDemuLogger()->debug(__VA_ARGS__);
#define DEMU_INFO(...) ::demu::Logger::getDemuLogger()->info(__VA_ARGS__);
#define DEMU_WARN(...) ::demu::Logger::getDemuLogger()->warn(__VA_ARGS__);
#define DEMU_CRIT(...) ::demu::Logger::getDemuLogger()->critical(__VA_ARGS__);
#define DEMU_ERROR(...)                                                        \
  do {                                                                         \
    ::demu::Logger::getDemuLogger()->error(__VA_ARGS__);                       \
    std::abort();                                                              \
  } while (0);

#define DEMU_CPU_TICK(cycle) DEMU_TRACE("CYCLE {:<6}", cycle)

#define DEMU_REG_WRITE(reg, val)                                               \
  DEMU_TRACE("REG [x{:02d}] <- 0x{:08x}", reg, val)

#define DEMU_MEM_TRACE(type, addr, data)                                       \
  DEMU_TRACE("[MEM {}] Addr: 0x{:08x} | Data: 0x{:08x}", type, addr, data)

#define DEMU_PIPE_STAGE(stage, pc, instr_name)                                 \
  DEMU_TRACE("{:<4} | PC: 0x{:08x} | [{}]", stage, pc, instr_name)

#define HAL_TRACE(...) ::demu::Logger::getHalLogger()->trace(__VA_ARGS__);
#define HAL_DEBUG(...) ::demu::Logger::getHalLogger()->debug(__VA_ARGS__);
#define HAL_INFO(...) ::demu::Logger::getHalLogger()->info(__VA_ARGS__);
#define HAL_WARN(...) ::demu::Logger::getHalLogger()->warn(__VA_ARGS__);
#define HAL_CRIT(...) ::demu::Logger::getHalLogger()->critical(__VA_ARGS__);
#define HAL_ERROR(...)                                                         \
  do {                                                                         \
    ::demu::Logger::getHalLogger()->error(__VA_ARGS__);                        \
    std::abort();                                                              \
  } while (0);
