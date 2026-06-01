// RUN: %bare_c
// RUN: %difftest -c 3000 | FileCheck %s

void print_str(const char *s) {
  char volatile *uart_txd = (char volatile *)(0x10000004);
  while (*s != '\0') {
    *uart_txd = *s;
    ++s;
  }
}

int main() {
  print_str("hello world\n");
  return 0;
}

// CHECK: hello world
