package com.sabimoni.core.data

import androidx.room.TypeConverter
import com.sabimoni.core.data.entity.ContributionStatus
import com.sabimoni.core.data.entity.Direction
import com.sabimoni.core.data.entity.GroupType
import com.sabimoni.core.data.entity.MessageSource
import com.sabimoni.core.data.entity.MoneySource
import com.sabimoni.core.data.entity.ParseStatus
import java.time.Instant
import java.time.LocalDate

class Converters {

    @TypeConverter
    fun instantToEpochMilli(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMilliToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun localDateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun messageSourceToName(value: MessageSource): String = value.name

    @TypeConverter
    fun nameToMessageSource(value: String): MessageSource = MessageSource.valueOf(value)

    @TypeConverter
    fun parseStatusToName(value: ParseStatus): String = value.name

    @TypeConverter
    fun nameToParseStatus(value: String): ParseStatus = ParseStatus.valueOf(value)

    @TypeConverter
    fun directionToName(value: Direction): String = value.name

    @TypeConverter
    fun nameToDirection(value: String): Direction = Direction.valueOf(value)

    @TypeConverter
    fun moneySourceToName(value: MoneySource): String = value.name

    @TypeConverter
    fun nameToMoneySource(value: String): MoneySource = MoneySource.valueOf(value)

    @TypeConverter
    fun groupTypeToName(value: GroupType): String = value.name

    @TypeConverter
    fun nameToGroupType(value: String): GroupType = GroupType.valueOf(value)

    @TypeConverter
    fun contributionStatusToName(value: ContributionStatus): String = value.name

    @TypeConverter
    fun nameToContributionStatus(value: String): ContributionStatus =
        ContributionStatus.valueOf(value)
}
