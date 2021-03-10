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

package android.view.translation;

import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.internal.util.DataClass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Translation request sent to the {@link android.service.translation.TranslationService} by the
 * {@link android.view.translation.Translator} which contains the text to be translated.
 */
@DataClass(genToString = true, genHiddenConstDefs = true, genBuilder = true)
public final class TranslationRequest implements Parcelable {

    /**
     * Indicates this request wants to receive the standard translation result.
     */
    public static final @RequestFlags int FLAG_TRANSLATION_RESULT = 0x1;
    /**
     * Indicates this request wants to receive the dictionary result.
     * TODO: describe the structure of the result.
     */
    public static final @RequestFlags int FLAG_DICTIONARY_RESULT = 0x2;
    /**
     * Indicates this request wants to receive the transliteration result.
     * TODO: describe the structure of the result.
     */
    public static final @RequestFlags int FLAG_TRANSLITERATION_RESULT = 0x4;
    /**
     * Indicates this request is willing to accept partial responses.
     *
     * <p>The partial responses can be accessed by
     * {@link TranslationResponse#getTranslationResponseValues()} or
     * {@link TranslationResponse#getViewTranslationResponses()}. These responses will each contain
     * only a subset of the corresponding translated values.
     *
     * <p>The are no guarantees to the number of translated values or the order in which these
     * values are returned in the {@link TranslationResponse}.
     *
     * <p>This flag denotes the client can expect multiple partial responses, but there may not
     * necessarily be multiple responses.</p>
     */
    public static final @RequestFlags int FLAG_PARTIAL_RESPONSES = 0x8;

    /**
     * Request flags. {@link #FLAG_TRANSLATION_RESULT} by default.
     */
    private final @RequestFlags int mFlags;

    /**
     * List of {@link TranslationRequestValue}s to be translated. The index of entries in this list
     * will be their respective key in the {@link android.util.SparseArray} returned by calling
     * {@link TranslationResponse#getTranslationResponseValues()}.
     */
    @NonNull
    @DataClass.PluralOf("translationRequestValue")
    private final List<TranslationRequestValue> mTranslationRequestValues;

    /**
     * List of {@link ViewTranslationRequest}s to be translated. The index of entries in this list
     * will be their respective key in the {@link android.util.SparseArray} returned by calling
     * {@link TranslationResponse#getViewTranslationResponses()}.
     */
    @NonNull
    @DataClass.PluralOf("viewTranslationRequest")
    private final List<ViewTranslationRequest> mViewTranslationRequests;

    private static int defaultFlags() {
        return FLAG_TRANSLATION_RESULT;
    }

    private static List<TranslationRequestValue> defaultTranslationRequestValues() {
        return Collections.emptyList();
    }

    private static List<ViewTranslationRequest> defaultViewTranslationRequests() {
        return Collections.emptyList();
    }



    // Code below generated by codegen v1.0.22.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/frameworks/base/core/java/android/view/translation/TranslationRequest.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    /** @hide */
    @android.annotation.IntDef(flag = true, prefix = "FLAG_", value = {
        FLAG_TRANSLATION_RESULT,
        FLAG_DICTIONARY_RESULT,
        FLAG_TRANSLITERATION_RESULT,
        FLAG_PARTIAL_RESPONSES
    })
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE)
    @DataClass.Generated.Member
    public @interface RequestFlags {}

    /** @hide */
    @DataClass.Generated.Member
    public static String requestFlagsToString(@RequestFlags int value) {
        return com.android.internal.util.BitUtils.flagsToString(
                value, TranslationRequest::singleRequestFlagsToString);
    }

    @DataClass.Generated.Member
    static String singleRequestFlagsToString(@RequestFlags int value) {
        switch (value) {
            case FLAG_TRANSLATION_RESULT:
                    return "FLAG_TRANSLATION_RESULT";
            case FLAG_DICTIONARY_RESULT:
                    return "FLAG_DICTIONARY_RESULT";
            case FLAG_TRANSLITERATION_RESULT:
                    return "FLAG_TRANSLITERATION_RESULT";
            case FLAG_PARTIAL_RESPONSES:
                    return "FLAG_PARTIAL_RESPONSES";
            default: return Integer.toHexString(value);
        }
    }

    @DataClass.Generated.Member
    /* package-private */ TranslationRequest(
            @RequestFlags int flags,
            @NonNull List<TranslationRequestValue> translationRequestValues,
            @NonNull List<ViewTranslationRequest> viewTranslationRequests) {
        this.mFlags = flags;

        com.android.internal.util.Preconditions.checkFlagsArgument(
                mFlags,
                FLAG_TRANSLATION_RESULT
                        | FLAG_DICTIONARY_RESULT
                        | FLAG_TRANSLITERATION_RESULT
                        | FLAG_PARTIAL_RESPONSES);
        this.mTranslationRequestValues = translationRequestValues;
        com.android.internal.util.AnnotationValidations.validate(
                NonNull.class, null, mTranslationRequestValues);
        this.mViewTranslationRequests = viewTranslationRequests;
        com.android.internal.util.AnnotationValidations.validate(
                NonNull.class, null, mViewTranslationRequests);

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * Request flags. {@link #FLAG_TRANSLATION_RESULT} by default.
     */
    @DataClass.Generated.Member
    public @RequestFlags int getFlags() {
        return mFlags;
    }

    /**
     * List of {@link TranslationRequestValue}s to be translated. The index of entries in this list
     * will be their respective key in the {@link android.util.SparseArray} returned by calling
     * {@link TranslationResponse#getTranslationResponseValues()}.
     */
    @DataClass.Generated.Member
    public @NonNull List<TranslationRequestValue> getTranslationRequestValues() {
        return mTranslationRequestValues;
    }

    /**
     * List of {@link ViewTranslationRequest}s to be translated. The index of entries in this list
     * will be their respective key in the {@link android.util.SparseArray} returned by calling
     * {@link TranslationResponse#getViewTranslationResponses()}.
     */
    @DataClass.Generated.Member
    public @NonNull List<ViewTranslationRequest> getViewTranslationRequests() {
        return mViewTranslationRequests;
    }

    @Override
    @DataClass.Generated.Member
    public String toString() {
        // You can override field toString logic by defining methods like:
        // String fieldNameToString() { ... }

        return "TranslationRequest { " +
                "flags = " + requestFlagsToString(mFlags) + ", " +
                "translationRequestValues = " + mTranslationRequestValues + ", " +
                "viewTranslationRequests = " + mViewTranslationRequests +
        " }";
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        dest.writeInt(mFlags);
        dest.writeParcelableList(mTranslationRequestValues, flags);
        dest.writeParcelableList(mViewTranslationRequests, flags);
    }

    @Override
    @DataClass.Generated.Member
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ TranslationRequest(@NonNull Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        int flags = in.readInt();
        List<TranslationRequestValue> translationRequestValues = new ArrayList<>();
        in.readParcelableList(translationRequestValues, TranslationRequestValue.class.getClassLoader());
        List<ViewTranslationRequest> viewTranslationRequests = new ArrayList<>();
        in.readParcelableList(viewTranslationRequests, ViewTranslationRequest.class.getClassLoader());

        this.mFlags = flags;

        com.android.internal.util.Preconditions.checkFlagsArgument(
                mFlags,
                FLAG_TRANSLATION_RESULT
                        | FLAG_DICTIONARY_RESULT
                        | FLAG_TRANSLITERATION_RESULT
                        | FLAG_PARTIAL_RESPONSES);
        this.mTranslationRequestValues = translationRequestValues;
        com.android.internal.util.AnnotationValidations.validate(
                NonNull.class, null, mTranslationRequestValues);
        this.mViewTranslationRequests = viewTranslationRequests;
        com.android.internal.util.AnnotationValidations.validate(
                NonNull.class, null, mViewTranslationRequests);

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @NonNull Parcelable.Creator<TranslationRequest> CREATOR
            = new Parcelable.Creator<TranslationRequest>() {
        @Override
        public TranslationRequest[] newArray(int size) {
            return new TranslationRequest[size];
        }

        @Override
        public TranslationRequest createFromParcel(@NonNull Parcel in) {
            return new TranslationRequest(in);
        }
    };

    /**
     * A builder for {@link TranslationRequest}
     */
    @SuppressWarnings("WeakerAccess")
    @DataClass.Generated.Member
    public static final class Builder {

        private @RequestFlags int mFlags;
        private @NonNull List<TranslationRequestValue> mTranslationRequestValues;
        private @NonNull List<ViewTranslationRequest> mViewTranslationRequests;

        private long mBuilderFieldsSet = 0L;

        public Builder() {
        }

        /**
         * Request flags. {@link #FLAG_TRANSLATION_RESULT} by default.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setFlags(@RequestFlags int value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x1;
            mFlags = value;
            return this;
        }

        /**
         * List of {@link TranslationRequestValue}s to be translated. The index of entries in this list
         * will be their respective key in the {@link android.util.SparseArray} returned by calling
         * {@link TranslationResponse#getTranslationResponseValues()}.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setTranslationRequestValues(@NonNull List<TranslationRequestValue> value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x2;
            mTranslationRequestValues = value;
            return this;
        }

        /** @see #setTranslationRequestValues */
        @DataClass.Generated.Member
        public @NonNull Builder addTranslationRequestValue(@NonNull TranslationRequestValue value) {
            if (mTranslationRequestValues == null) setTranslationRequestValues(new ArrayList<>());
            mTranslationRequestValues.add(value);
            return this;
        }

        /**
         * List of {@link ViewTranslationRequest}s to be translated. The index of entries in this list
         * will be their respective key in the {@link android.util.SparseArray} returned by calling
         * {@link TranslationResponse#getViewTranslationResponses()}.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setViewTranslationRequests(@NonNull List<ViewTranslationRequest> value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x4;
            mViewTranslationRequests = value;
            return this;
        }

        /** @see #setViewTranslationRequests */
        @DataClass.Generated.Member
        public @NonNull Builder addViewTranslationRequest(@NonNull ViewTranslationRequest value) {
            if (mViewTranslationRequests == null) setViewTranslationRequests(new ArrayList<>());
            mViewTranslationRequests.add(value);
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @NonNull TranslationRequest build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x8; // Mark builder used

            if ((mBuilderFieldsSet & 0x1) == 0) {
                mFlags = defaultFlags();
            }
            if ((mBuilderFieldsSet & 0x2) == 0) {
                mTranslationRequestValues = defaultTranslationRequestValues();
            }
            if ((mBuilderFieldsSet & 0x4) == 0) {
                mViewTranslationRequests = defaultViewTranslationRequests();
            }
            TranslationRequest o = new TranslationRequest(
                    mFlags,
                    mTranslationRequestValues,
                    mViewTranslationRequests);
            return o;
        }

        private void checkNotUsed() {
            if ((mBuilderFieldsSet & 0x8) != 0) {
                throw new IllegalStateException(
                        "This Builder should not be reused. Use a new Builder instance instead");
            }
        }
    }

    @DataClass.Generated(
            time = 1614132376448L,
            codegenVersion = "1.0.22",
            sourceFile = "frameworks/base/core/java/android/view/translation/TranslationRequest.java",
            inputSignatures = "public static final @android.view.translation.TranslationRequest.RequestFlags int FLAG_TRANSLATION_RESULT\npublic static final @android.view.translation.TranslationRequest.RequestFlags int FLAG_DICTIONARY_RESULT\npublic static final @android.view.translation.TranslationRequest.RequestFlags int FLAG_TRANSLITERATION_RESULT\npublic static final @android.view.translation.TranslationRequest.RequestFlags int FLAG_PARTIAL_RESPONSES\nprivate final @android.view.translation.TranslationRequest.RequestFlags int mFlags\nprivate final @android.annotation.NonNull @com.android.internal.util.DataClass.PluralOf(\"translationRequestValue\") java.util.List<android.view.translation.TranslationRequestValue> mTranslationRequestValues\nprivate final @android.annotation.NonNull @com.android.internal.util.DataClass.PluralOf(\"viewTranslationRequest\") java.util.List<android.view.translation.ViewTranslationRequest> mViewTranslationRequests\nprivate static  int defaultFlags()\nprivate static  java.util.List<android.view.translation.TranslationRequestValue> defaultTranslationRequestValues()\nprivate static  java.util.List<android.view.translation.ViewTranslationRequest> defaultViewTranslationRequests()\nclass TranslationRequest extends java.lang.Object implements [android.os.Parcelable]\n@com.android.internal.util.DataClass(genToString=true, genHiddenConstDefs=true, genBuilder=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
