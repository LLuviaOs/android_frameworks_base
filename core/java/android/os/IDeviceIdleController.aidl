/**
 * Copyright (c) 2015, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.os;

import android.os.IMaintenanceActivityListener;
import android.os.UserHandle;

/** @hide */
interface IDeviceIdleController {
    void addPowerSaveWhitelistApp(String name);
    void removePowerSaveWhitelistApp(String name);
    /* Removes an app from the system whitelist. Calling restoreSystemPowerWhitelistApp will add
    the app back into the system whitelist */
    void removeSystemPowerWhitelistApp(String name);
    void restoreSystemPowerWhitelistApp(String name);
    String[] getRemovedSystemPowerWhitelistApps();
    String[] getSystemPowerWhitelistExceptIdle();
    String[] getSystemPowerWhitelist();
    String[] getUserPowerWhitelist();
    @UnsupportedAppUsage
    String[] getFullPowerWhitelistExceptIdle();
    String[] getFullPowerWhitelist();
    int[] getAppIdWhitelistExceptIdle();
    int[] getAppIdWhitelist();
    int[] getAppIdUserWhitelist();
    @UnsupportedAppUsage
    int[] getAppIdTempWhitelist();
    boolean isPowerSaveWhitelistExceptIdleApp(String name);
    boolean isPowerSaveWhitelistApp(String name);
    @UnsupportedAppUsage
    void addPowerSaveTempWhitelistApp(String name, long duration, int userId, String reason);
    long addPowerSaveTempWhitelistAppForMms(String name, int userId, String reason);
    long addPowerSaveTempWhitelistAppForSms(String name, int userId, String reason);
    void exitIdle(String reason);
    boolean registerMaintenanceActivityListener(IMaintenanceActivityListener listener);
    void unregisterMaintenanceActivityListener(IMaintenanceActivityListener listener);
    int setPreIdleTimeoutMode(int Mode);
    void resetPreIdleTimeoutMode();
    int getIdleStateDetailed();
    int getLightIdleStateDetailed();
}
