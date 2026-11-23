// SPDX-License-Identifier: Apache-2.0
#include <windows.h>

#include <iostream>
#include <string>

int wmain() {
    wchar_t executable[32768];
    DWORD length = GetModuleFileNameW(nullptr, executable, 32768);
    if (length == 0 || length >= 32768) return ERROR_INVALID_NAME;
    std::wstring directory(executable, length);
    directory.resize(directory.find_last_of(L"\\/") + 1);

    HMODULE newdev = LoadLibraryExW(L"newdev.dll", nullptr, LOAD_LIBRARY_SEARCH_SYSTEM32);
    if (!newdev) return GetLastError();
    using InstallDriver = BOOL (WINAPI *)(HWND, LPCWSTR, DWORD, PBOOL);
    auto install = reinterpret_cast<InstallDriver>(GetProcAddress(newdev, "DiInstallDriverW"));
    if (!install) return GetLastError();

    bool restart = false;
    for (const wchar_t *relative : {L"drivers\\ftdi\\ftdibus.inf", L"drivers\\xilinx\\xpcwinusb.inf"}) {
        std::wstring inf = directory + relative;
        DWORD attributes = GetFileAttributesW(inf.c_str());
        if (attributes == INVALID_FILE_ATTRIBUTES) {
            DWORD error = GetLastError();
            if (std::wstring(relative) == L"drivers\\xilinx\\xpcwinusb.inf"
                && (error == ERROR_FILE_NOT_FOUND || error == ERROR_PATH_NOT_FOUND)) continue;
            return error;
        }
        BOOL needsReboot = FALSE;
        // Let Windows verify the signed catalog and select the best driver.
        if (!install(nullptr, inf.c_str(), 0, &needsReboot)) {
            DWORD error = GetLastError();
            std::wcerr << L"Driver setup failed for " << relative << L" (" << error << L")\n";
            return error;
        }
        restart |= needsReboot != FALSE;
    }
    return restart ? ERROR_SUCCESS_REBOOT_REQUIRED : ERROR_SUCCESS;
}
