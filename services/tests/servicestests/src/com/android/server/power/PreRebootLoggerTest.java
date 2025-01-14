/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.power;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.content.Context;
import android.provider.Settings;
import android.test.mock.MockContentResolver;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.util.test.FakeSettingsProvider;

import com.google.common.io.Files;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;

/**
 * Tests for {@link PreRebootLogger}
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class PreRebootLoggerTest {
    @Mock Context mContext;
    private MockContentResolver mContentResolver;
    private File mDumpDir;

    @BeforeClass
    public static void setupOnce() {
        FakeSettingsProvider.clearSettingsProvider();
    }

    @AfterClass
    public static void tearDownOnce() {
        FakeSettingsProvider.clearSettingsProvider();
    }

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mContentResolver = new MockContentResolver(getInstrumentation().getTargetContext());
        when(mContext.getContentResolver()).thenReturn(mContentResolver);
        mContentResolver.addProvider(Settings.AUTHORITY, new FakeSettingsProvider());

        mDumpDir = Files.createTempDir();
        mDumpDir.mkdir();
        mDumpDir.deleteOnExit();
    }

    @Test
    public void log_adbEnabled_dumpsInformationProperly() {
        Settings.Global.putInt(mContentResolver, Settings.Global.ADB_ENABLED, 1);

        PreRebootLogger.log(mContext, mDumpDir);

        assertThat(mDumpDir.list()).asList().containsExactly("system", "package", "rollback");
    }

    @Test
    public void log_adbDisabled_wipesDumpedInformation() {
        Settings.Global.putInt(mContentResolver, Settings.Global.ADB_ENABLED, 1);
        PreRebootLogger.log(mContext, mDumpDir);
        Settings.Global.putInt(mContentResolver, Settings.Global.ADB_ENABLED, 0);

        PreRebootLogger.log(mContext, mDumpDir);

        assertThat(mDumpDir.listFiles()).isEmpty();
    }
}
