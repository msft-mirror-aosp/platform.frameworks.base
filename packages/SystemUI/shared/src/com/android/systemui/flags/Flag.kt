/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.flags

import android.annotation.BoolRes
import android.annotation.IntegerRes
import android.annotation.StringRes
import android.os.Parcel
import android.os.Parcelable

interface Flag<T> {
    val id: Int
}

interface ParcelableFlag<T> : Flag<T>, Parcelable {
    val default: T
    override fun describeContents() = 0
}

interface ResourceFlag<T> : Flag<T> {
    val resourceId: Int
}

// Consider using the "parcelize" kotlin library.

data class BooleanFlag @JvmOverloads constructor(
    override val id: Int,
    override val default: Boolean = false
) : ParcelableFlag<Boolean> {

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<BooleanFlag> {
            override fun createFromParcel(parcel: Parcel) = BooleanFlag(parcel)
            override fun newArray(size: Int) = arrayOfNulls<BooleanFlag>(size)
        }
    }

    private constructor(parcel: Parcel) : this(
        id = parcel.readInt(),
        default = parcel.readBoolean()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)
        parcel.writeBoolean(default)
    }
}

data class ResourceBooleanFlag constructor(
    override val id: Int,
    @BoolRes override val resourceId: Int
) : ResourceFlag<Boolean>

data class StringFlag @JvmOverloads constructor(
    override val id: Int,
    override val default: String = ""
) : ParcelableFlag<String> {
    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<StringFlag> {
            override fun createFromParcel(parcel: Parcel) = StringFlag(parcel)
            override fun newArray(size: Int) = arrayOfNulls<StringFlag>(size)
        }
    }

    private constructor(parcel: Parcel) : this(
        id = parcel.readInt(),
        default = parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)
        parcel.writeString(default)
    }
}

data class ResourceStringFlag constructor(
    override val id: Int,
    @StringRes override val resourceId: Int
) : ResourceFlag<String>

data class IntFlag @JvmOverloads constructor(
    override val id: Int,
    override val default: Int = 0
) : ParcelableFlag<Int> {

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<IntFlag> {
            override fun createFromParcel(parcel: Parcel) = IntFlag(parcel)
            override fun newArray(size: Int) = arrayOfNulls<IntFlag>(size)
        }
    }

    private constructor(parcel: Parcel) : this(
        id = parcel.readInt(),
        default = parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)
        parcel.writeInt(default)
    }
}

data class ResourceIntFlag constructor(
    override val id: Int,
    @IntegerRes override val resourceId: Int
) : ResourceFlag<Int>

data class LongFlag @JvmOverloads constructor(
    override val id: Int,
    override val default: Long = 0
) : ParcelableFlag<Long> {

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<LongFlag> {
            override fun createFromParcel(parcel: Parcel) = LongFlag(parcel)
            override fun newArray(size: Int) = arrayOfNulls<LongFlag>(size)
        }
    }

    private constructor(parcel: Parcel) : this(
        id = parcel.readInt(),
        default = parcel.readLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)
        parcel.writeLong(default)
    }
}

data class FloatFlag @JvmOverloads constructor(
    override val id: Int,
    override val default: Float = 0f
) : ParcelableFlag<Float> {

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<FloatFlag> {
            override fun createFromParcel(parcel: Parcel) = FloatFlag(parcel)
            override fun newArray(size: Int) = arrayOfNulls<FloatFlag>(size)
        }
    }

    private constructor(parcel: Parcel) : this(
        id = parcel.readInt(),
        default = parcel.readFloat()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)
        parcel.writeFloat(default)
    }
}

data class ResourceFloatFlag constructor(
    override val id: Int,
    override val resourceId: Int
) : ResourceFlag<Int>

data class DoubleFlag @JvmOverloads constructor(
    override val id: Int,
    override val default: Double = 0.0
) : ParcelableFlag<Double> {

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<DoubleFlag> {
            override fun createFromParcel(parcel: Parcel) = DoubleFlag(parcel)
            override fun newArray(size: Int) = arrayOfNulls<DoubleFlag>(size)
        }
    }

    private constructor(parcel: Parcel) : this(
        id = parcel.readInt(),
        default = parcel.readDouble()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)
        parcel.writeDouble(default)
    }
}