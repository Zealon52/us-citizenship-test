package com.usctest.app.data.local

import androidx.room.TypeConverter
import com.usctest.app.data.model.TestVersion

class Converters {
    @TypeConverter
    fun fromTestVersion(value: TestVersion): String = value.name

    @TypeConverter
    fun toTestVersion(value: String): TestVersion = TestVersion.valueOf(value)

    @TypeConverter
    fun fromMasteryLevel(value: MasteryLevel): String = value.name

    @TypeConverter
    fun toMasteryLevel(value: String): MasteryLevel = MasteryLevel.valueOf(value)

    @TypeConverter
    fun fromAttemptMode(value: AttemptMode): String = value.name

    @TypeConverter
    fun toAttemptMode(value: String): AttemptMode = AttemptMode.valueOf(value)

    @TypeConverter
    fun fromInputMethod(value: InputMethod?): String? = value?.name

    @TypeConverter
    fun toInputMethod(value: String?): InputMethod? = value?.let { InputMethod.valueOf(it) }
}
