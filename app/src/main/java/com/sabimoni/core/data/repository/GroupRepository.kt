package com.sabimoni.core.data.repository

import com.sabimoni.core.data.dao.GroupContributionDao
import com.sabimoni.core.data.dao.GroupDao
import com.sabimoni.core.data.entity.GroupEntity
import com.sabimoni.core.data.entity.GroupType
import com.sabimoni.core.data.model.MoneyGroup
import com.sabimoni.core.money.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepository @Inject constructor(
    private val groupDao: GroupDao,
    private val contributionDao: GroupContributionDao,
) {

    fun observeGroups(): Flow<List<MoneyGroup>> =
        groupDao.observeActive().map { groups -> groups.map(GroupEntity::toDomain) }

    fun observeTotalOutstanding(): Flow<Money> =
        contributionDao.observeTotalOutstanding().map(::Money)

    suspend fun createGroup(
        name: String,
        type: GroupType = GroupType.CONTRIBUTION,
        penalty: Money? = null,
        reminderLeadDays: Int = 2,
    ): Long = groupDao.upsert(
        GroupEntity(
            name = name,
            type = type,
            penaltyXaf = penalty?.xaf,
            reminderLeadDays = reminderLeadDays,
        ),
    )
}

private fun GroupEntity.toDomain() = MoneyGroup(
    id = id,
    name = name,
    type = type,
    penalty = penaltyXaf?.let(::Money),
    reminderLeadDays = reminderLeadDays,
)
