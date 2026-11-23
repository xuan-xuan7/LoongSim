// SPDX-License-Identifier: Apache-2.0
#pragma once

// Windows transport for openFPGALoader's existing FTDI MPSSE encoder.
#include <windows.h>
#undef interface
#include <cstdint>
#include <string>
#include <vector>

constexpr uint8_t BITMODE_RESET = 0, BITMODE_MPSSE = 2;
constexpr uint8_t MPSSE_WRITE_NEG = 0x01, MPSSE_BITMODE = 0x02, MPSSE_READ_NEG = 0x04;
constexpr uint8_t MPSSE_LSB = 0x08, MPSSE_DO_WRITE = 0x10, MPSSE_DO_READ = 0x20;
constexpr uint8_t MPSSE_WRITE_TMS = 0x40;
constexpr uint8_t SET_BITS_LOW = 0x80, GET_BITS_LOW = 0x81;
constexpr uint8_t SET_BITS_HIGH = 0x82, GET_BITS_HIGH = 0x83;
constexpr uint8_t LOOPBACK_START = 0x84, LOOPBACK_END = 0x85, TCK_DIVISOR = 0x86;
constexpr uint8_t SEND_IMMEDIATE = 0x87, DIS_DIV_5 = 0x8a;
constexpr uint8_t CLK_NBITS = 0x8e, CLK_NBYTES = 0x8f;
constexpr uint8_t DIS_ADAPTIVE = 0x97, DIS_3_PHASE = 0x8d;

enum ftdi_chip_type { TYPE_2232C, TYPE_2232H, TYPE_4232H, TYPE_232H };
enum ftdi_interface { INTERFACE_ANY, INTERFACE_A, INTERFACE_B };

struct D2xxCable {
    std::string profile;
    std::string serial;
    std::string description;
    DWORD id;
    DWORD flags;
};

std::vector<D2xxCable> scanD2xx();
int printD2xxScan();

struct ftdi_context {
    HANDLE handle = nullptr;
    ftdi_interface interface = INTERFACE_A;
    ftdi_chip_type type = TYPE_232H;
    int max_packet_size = 512;
    std::string product;
    std::string serial;
    std::string error;
};

ftdi_context *ftdi_new();
void ftdi_free(ftdi_context *device);
const char *ftdi_get_error_string(ftdi_context *device);
int ftdi_set_interface(ftdi_context *device, ftdi_interface interface);
int ftdi_usb_open_desc_index(ftdi_context *device, int vid, int pid, const char *description,
                            const char *serial, unsigned int index);
int ftdi_usb_open_desc(ftdi_context *device, int vid, int pid, const char *description,
                     const char *serial);
int ftdi_usb_open_bus_addr(ftdi_context *device, int bus, int address);
int ftdi_usb_close(ftdi_context *device);
int ftdi_usb_reset(ftdi_context *device);
int ftdi_set_baudrate(ftdi_context *device, int baud);
int ftdi_set_bitmode(ftdi_context *device, unsigned char mask, unsigned char mode);
int ftdi_set_latency_timer(ftdi_context *device, unsigned char latency);
int ftdi_tciflush(ftdi_context *device);
int ftdi_tcoflush(ftdi_context *device);
int ftdi_tcioflush(ftdi_context *device);
int ftdi_read_data_set_chunksize(ftdi_context *device, unsigned int size);
int ftdi_write_data_set_chunksize(ftdi_context *device, unsigned int size);
int ftdi_read_data(ftdi_context *device, unsigned char *buffer, int size);
int ftdi_write_data(ftdi_context *device, const unsigned char *buffer, int size);
