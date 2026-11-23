// SPDX-License-Identifier: Apache-2.0
#include "ftdi-d2xx.hpp"

#include <algorithm>
#include <cctype>
#include <iostream>
#include <stdexcept>

namespace {
struct D2xxDeviceInfo {
    DWORD flags, type, id, location;
    char serial[16];
    char description[64];
    HANDLE handle;
};

class D2xx {
public:
    D2xx() : module(LoadLibraryExW(L"ftd2xx.dll", nullptr, LOAD_LIBRARY_SEARCH_SYSTEM32)) {}
    ~D2xx() { if (module) FreeLibrary(module); }

    template<typename Function> Function function(const char *name) const {
        auto entry = module ? GetProcAddress(module, name) : nullptr;
        if (!entry) throw std::runtime_error(std::string("FTDI D2XX entry unavailable: ") + name);
        return reinterpret_cast<Function>(entry);
    }

    HMODULE const module;
};

const D2xx &api() {
    static const D2xx d2xx;
    return d2xx;
}

int result(ftdi_context *device, const char *operation, DWORD status) {
    if (status == 0) return 0;
    device->error = std::string(operation) + " (FTDI status " + std::to_string(status) + ")";
    return -1;
}

int deviceCommand(ftdi_context *device, const char *name) {
    return result(device, name, api().function<DWORD (WINAPI *)(HANDLE)>(name)(device->handle));
}

int setting(ftdi_context *device, const char *name, DWORD value) {
    return result(device, name, api().function<DWORD (WINAPI *)(HANDLE, DWORD)>(name)(device->handle, value));
}

std::string profileFor(DWORD id, std::string label, const std::string &serial) {
    std::transform(label.begin(), label.end(), label.begin(), [](unsigned char c) { return std::tolower(c); });
    if (label.find("digilent") == std::string::npos && label.find("jtag-") == std::string::npos) return {};
    if (id == 0x04036010 && !serial.empty() && serial.back() == 'A') return "digilent";
    if (id != 0x04036014) return {};
    if (label.find("hs3") != std::string::npos) return "digilent_hs3";
    if (label.find("hs2") != std::string::npos || label.find("smt2") != std::string::npos
        || serial.compare(0, 6, "210251") == 0) return "digilent_hs2";
    return {};
}
} // namespace

std::vector<D2xxCable> scanD2xx() {
    std::vector<D2xxCable> cables;
    if (!api().module) return cables;
    DWORD count = 0;
    auto createList = api().function<DWORD (WINAPI *)(DWORD *)>("FT_CreateDeviceInfoList");
    if (createList(&count) != 0) throw std::runtime_error("FTDI D2XX enumeration failed");
    if (!count) return cables;
    std::vector<D2xxDeviceInfo> devices(count);
    auto getList = api().function<DWORD (WINAPI *)(D2xxDeviceInfo *, DWORD *)>("FT_GetDeviceInfoList");
    if (getList(devices.data(), &count) != 0 || count > devices.size()) {
        throw std::runtime_error("FTDI D2XX device list changed. Try again.");
    }
    for (size_t index = 0; index < count; ++index) {
        auto &device = devices[index];
        device.serial[sizeof(device.serial) - 1] = '\0';
        device.description[sizeof(device.description) - 1] = '\0';
        std::string serial(device.serial);
        if (serial.empty() || serial.find_first_not_of("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz") != std::string::npos) continue;
        std::string profile = profileFor(device.id, device.description, serial);
        if (!profile.empty()) cables.push_back({profile, serial, device.description, device.id, device.flags});
    }
    return cables;
}

int printD2xxScan() {
    try {
        for (const auto &cable : scanD2xx()) {
            std::cout << "LoongSim D2XX " << cable.profile << ' ' << cable.serial << '\n';
        }
        return 0;
    } catch (const std::exception &ex) {
        std::cerr << ex.what() << '\n';
        return 1;
    }
}

ftdi_context *ftdi_new() { return new ftdi_context; }
void ftdi_free(ftdi_context *device) {
    if (device->handle) ftdi_usb_close(device);
    delete device;
}
const char *ftdi_get_error_string(ftdi_context *device) { return device->error.c_str(); }
int ftdi_set_interface(ftdi_context *device, ftdi_interface interface) {
    device->interface = interface;
    return 0;
}

int ftdi_usb_open_desc_index(ftdi_context *device, int vid, int pid, const char *description,
                            const char *serial, unsigned int index) {
    try {
        for (const auto &cable : scanD2xx()) {
            if (cable.id != (static_cast<DWORD>(vid) << 16 | static_cast<DWORD>(pid))
                || (description && cable.description != description) || (serial && cable.serial != serial)
                || device->interface != INTERFACE_A) continue;
            if (index > 0) {
                --index;
                continue;
            }
            if (cable.flags & 1) {
                device->error = "Digilent USB device is in use by another application";
                return -1;
            }
            auto open = api().function<DWORD (WINAPI *)(const char *, DWORD, HANDLE *)>("FT_OpenEx");
            if (result(device, "FT_OpenEx", open(cable.serial.c_str(), 1, &device->handle)) != 0) return -1;
            device->product = cable.description;
            device->serial = cable.serial;
            device->type = pid == 0x6010 ? TYPE_2232H : TYPE_232H;
            auto timeouts = api().function<DWORD (WINAPI *)(HANDLE, DWORD, DWORD)>("FT_SetTimeouts");
            return result(device, "FT_SetTimeouts", timeouts(device->handle, 150, 3000));
        }
        device->error = "No matching Digilent device in FTDI D2XX";
    } catch (const std::exception &ex) {
        device->error = ex.what();
    }
    return -1;
}

int ftdi_usb_open_desc(ftdi_context *device, int vid, int pid, const char *description,
                     const char *serial) {
    return ftdi_usb_open_desc_index(device, vid, pid, description, serial, 0);
}
int ftdi_usb_open_bus_addr(ftdi_context *device, int, int) {
    device->error = "Use the Digilent serial number for D2XX";
    return -1;
}

int ftdi_usb_close(ftdi_context *device) {
    int status = deviceCommand(device, "FT_Close");
    device->handle = nullptr;
    return status;
}
int ftdi_usb_reset(ftdi_context *device) { return deviceCommand(device, "FT_ResetDevice"); }
int ftdi_set_baudrate(ftdi_context *device, int baud) { return setting(device, "FT_SetBaudRate", baud); }
int ftdi_set_bitmode(ftdi_context *device, unsigned char mask, unsigned char mode) {
    auto set = api().function<DWORD (WINAPI *)(HANDLE, UCHAR, UCHAR)>("FT_SetBitMode");
    return result(device, "FT_SetBitMode", set(device->handle, mask, mode));
}
int ftdi_set_latency_timer(ftdi_context *device, unsigned char latency) {
    auto set = api().function<DWORD (WINAPI *)(HANDLE, UCHAR)>("FT_SetLatencyTimer");
    return result(device, "FT_SetLatencyTimer", set(device->handle, latency));
}
int ftdi_tciflush(ftdi_context *device) { return setting(device, "FT_Purge", 1); }
int ftdi_tcoflush(ftdi_context *device) { return setting(device, "FT_Purge", 2); }
int ftdi_tcioflush(ftdi_context *device) { return setting(device, "FT_Purge", 3); }

// D2XX manages its USB transfer buffers internally.
int ftdi_read_data_set_chunksize(ftdi_context *, unsigned int) { return 0; }
int ftdi_write_data_set_chunksize(ftdi_context *, unsigned int) { return 0; }

int ftdi_read_data(ftdi_context *device, unsigned char *buffer, int size) {
    DWORD received = 0;
    auto read = api().function<DWORD (WINAPI *)(HANDLE, LPVOID, DWORD, DWORD *)>("FT_Read");
    if (result(device, "FT_Read", read(device->handle, buffer, size, &received)) != 0) return -1;
    return static_cast<int>(received);
}
int ftdi_write_data(ftdi_context *device, const unsigned char *buffer, int size) {
    DWORD written = 0;
    auto write = api().function<DWORD (WINAPI *)(HANDLE, const void *, DWORD, DWORD *)>("FT_Write");
    if (result(device, "FT_Write", write(device->handle, buffer, size, &written)) != 0) return -1;
    return static_cast<int>(written);
}
