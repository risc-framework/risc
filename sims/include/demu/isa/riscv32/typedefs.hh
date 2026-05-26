#pragma once

#include <cstdint>

#if defined(__ISA_RV32I__)
#include "Vrv32i_system.h"

#elif defined(__ISA_RV32IM__)
#include "Vrv32im_system.h"

#endif

namespace demu::isa {
enum { NUM_GPRS = 32, INSTR_ALIGNMENT = 4 };

#if defined(__ISA_RV32I__)
using system_t = Vrv32i_system;

#elif defined(__ISA_RV32IM__)
using system_t = Vrv32im_system;

#endif

using instr_t = uint32_t;
using addr_t = uint32_t;
using word_t = uint32_t;
using half_t = uint16_t;
using byte_t = uint8_t;

} // namespace demu::isa
