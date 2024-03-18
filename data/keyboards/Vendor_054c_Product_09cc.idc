# Copyright (C) 2020 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#
# Sony Playstation(R) DualShock 4 Controller
#

## Motion sensor ##

# reporting mode 0 - continuous
sensor.accelerometer.reportingMode = 0
# The delay between sensor events corresponding to the lowest frequency in microsecond
sensor.accelerometer.maxDelay = 100000
# The minimum delay allowed between two events in microsecond
sensor.accelerometer.minDelay = 5000
# The power in mA used by this sensor while in use
sensor.accelerometer.power = 1.5

# reporting mode 0 - continuous
sensor.gyroscope.reportingMode = 0
# The delay between sensor events corresponding to the lowest frequency in microsecond
sensor.gyroscope.maxDelay = 100000
# The minimum delay allowed between two events in microsecond
sensor.gyroscope.minDelay = 5000
# The power in mA used by this sensor while in use
sensor.gyroscope.power = 0.8

## Touchpad ##

# After the DualShock 4 has been connected over Bluetooth for a minute or so,
# its reports start bunching up in time, meaning that we receive 2–4 reports
# within a millisecond followed by a >10ms wait until the next batch.
#
# This uneven timing causes the apparent speed of a finger (calculated using
# time deltas between received reports) to vary dramatically even if it's
# actually moving smoothly across the touchpad, triggering the touchpad stack's
# drumroll detection logic, which causes the finger's single smooth movement to
# be treated as many small movements of consecutive touches, which are then
# inhibited by the click wiggle filter.
#
# Since this touchpad does not seem vulnerable to click wiggle, we can safely
# disable drumroll detection due to speed changes (by setting the speed change
# threshold very high, since there's no boolean control property).
gestureProp.Drumroll_Max_Speed_Change_Factor = 1000000000
