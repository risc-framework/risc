#pragma once

#include "demu/logger.hh"
#include "ref_model.hh"
#include <arpa/inet.h>
#include <iostream>
#include <netinet/tcp.h>
#include <sstream>
#include <string>
#include <sys/socket.h>
#include <unistd.h>

namespace demu::difftest {

class GdbRefModel final : public IRefModel {
public:
  explicit GdbRefModel(int port = 1234) {
    sock_ = socket(AF_INET, SOCK_STREAM, 0);

    int flag = 1;
    setsockopt(sock_, IPPROTO_TCP, TCP_NODELAY, (char *)&flag, sizeof(int));

    struct sockaddr_in serv_addr{};
    serv_addr.sin_family = AF_INET;
    serv_addr.sin_port = htons(port);
    inet_pton(AF_INET, "127.0.0.1", &serv_addr.sin_addr);

    DEMU_INFO("Connecting to QEMU GDB on localhost:{} ...", port);
    if (connect(sock_, reinterpret_cast<struct sockaddr *>(&serv_addr),
                sizeof(serv_addr)) < 0) {
      DEMU_ERROR("Connection failed! Is QEMU running with '-s -S'?");
      exit(1);
    }
    DEMU_INFO("Connected to QEMU successfully!");
  }

  ~GdbRefModel() override {
    if (sock_ >= 0) {
      close(sock_);
    }
  }

  auto init() -> bool override {
    send_packet("?");
    recv_packet();

    send_packet("QStartNoAckMode");
    std::string reply = recv_packet();
    if (reply == "OK") {
      no_ack_mode_ = true;
      DEMU_INFO("GDB No-Ack mode enabled. Networking optimized.");
    }
    return true;
  }

  void sync_memory(addr_t addr, size_t size, const void *data) override {
    const auto *bytes = static_cast<const byte_t *>(data);
    std::stringstream ss;
    ss << "M" << std::hex << addr << "," << size << ":";
    for (size_t i = 0; i < size; i++) {
      char buf[3];
      snprintf(buf, sizeof(buf), "%02x", bytes[i]);
      ss << buf;
    }
    send_packet(ss.str());
    recv_packet();
  }

  void step(uint64_t n) override {
    for (uint64_t i = 0; i < n; ++i) {
      send_packet("s");
      recv_packet();
    }
  }

  void push_state() override {
    std::stringstream ss;
    ss << "G";
    auto encode_hex_le = [](word_t val) {
      char buf[9];
      snprintf(buf, sizeof(buf), "%02x%02x%02x%02x", val & 0xFF,
               (val >> 8) & 0xFF, (val >> 16) & 0xFF, (val >> 24) & 0xFF);
      return std::string(buf);
    };

    for (int i = 0; i < isa_def::NUM_ARCH_REGS; i++) {
      ss << encode_hex_le(state_.gpr[i]);
    }
    ss << encode_hex_le(static_cast<word_t>(state_.pc));

    send_packet(ss.str());
    recv_packet(); // Wait for 'OK'
  }

  void pull_state() override {
    send_packet("g");
    std::string regs_hex = recv_packet();

    for (int i = 0; i < isa_def::NUM_ARCH_REGS; i++) {
      state_.gpr[i] = decode_hex_le(regs_hex, i * 8);
    }
    state_.pc = static_cast<addr_t>(
        decode_hex_le(regs_hex, isa_def::NUM_ARCH_REGS * 8));
  }

  [[nodiscard]] auto get_pc() const -> addr_t override { return state_.pc; }

  [[nodiscard]] auto get_reg(uint8_t idx) const -> word_t override {
    return state_.gpr[idx];
  }

  void set_pc(addr_t pc) override { state_.pc = pc; }

  void set_reg(uint8_t idx, word_t val) override { state_.gpr[idx] = val; }

private:
  int sock_ = -1;
  CPU_state state_{};
  bool no_ack_mode_ = false;

  char recv_buf_[8192];
  size_t recv_pos_ = 0;
  size_t recv_len_ = 0;

  auto checksum(const std::string &data) -> std::string {
    byte_t csum = 0;
    for (char c : data) {
      csum += static_cast<byte_t>(c);
    }
    char buf[4];
    snprintf(buf, sizeof(buf), "%02x", csum);
    return buf;
  }

  inline char read_char() {
    if (recv_pos_ >= recv_len_) {
      ssize_t bytes = recv(sock_, recv_buf_, sizeof(recv_buf_), 0);
      if (bytes <= 0) {
        DEMU_ERROR("QEMU GDB Socket closed or error.");
        exit(1);
      }
      recv_pos_ = 0;
      recv_len_ = static_cast<size_t>(bytes);
    }
    return recv_buf_[recv_pos_++];
  }

  void send_packet(const std::string &data) {
    std::string packet = "$" + data + "#" + checksum(data);
    send(sock_, packet.c_str(), packet.length(), 0);

    if (!no_ack_mode_) {
      char ack = read_char();
      if (ack != '+') {
        DEMU_ERROR("GDB: Invalid ACK received: {}", ack);
      }
    }
  }

  auto recv_packet() -> std::string {
    char c;
    std::string data;
    data.reserve(256);

    while ((c = read_char()) != '$') {
    }

    while ((c = read_char()) != '#') {
      data += c;
    }

    read_char();
    read_char();

    if (!no_ack_mode_) {
      c = '+';
      send(sock_, &c, 1, 0); // Only send ACK if mode is off
    }
    return data;
  }

  auto decode_hex_le(const std::string &hex, size_t offset) -> word_t {
    if (offset + 8 > hex.length()) {
      return 0;
    }

    const char *ptr = hex.data() + offset;
    word_t val = 0;

    for (int i = 0; i < 4; i++) {
      uint8_t byte_val = (hex2val(ptr[0]) << 4) | hex2val(ptr[1]);
      val |= (static_cast<word_t>(byte_val) << (i * 8));
      ptr += 2;
    }
    return val;
  }

  [[nodiscard]] inline uint8_t hex2val(char c) const {
    if (c >= '0' && c <= '9') {
      return c - '0';
    }
    if (c >= 'a' && c <= 'f') {
      return c - 'a' + 10;
    }
    if (c >= 'A' && c <= 'F') {
      return c - 'A' + 10;
    }
    return 0;
  }
};

inline auto create_ref_model(const std::string &path)
    -> std::unique_ptr<IRefModel> {
  if (path == "gdb") {
    return std::make_unique<GdbRefModel>(1234);
  }

  return nullptr;
}

} // namespace demu::difftest
