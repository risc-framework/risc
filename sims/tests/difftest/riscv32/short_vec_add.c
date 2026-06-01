// RUN: %bare_c
// RUN: %difftest -c 5000

int main() {
  int a[16], b[16], c[16];

  for (int i = 0; i < 16; i++) {
    a[i] = i;
    b[i] = 2 * i;
  }

  for (int i = 0; i < 16; i++) {
    c[i] = a[i] + b[i];
  }

  return 0;
}
