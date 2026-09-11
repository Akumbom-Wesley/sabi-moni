package com.sabimoni.core.data.entity

enum class MessageSource { TYPED, SMS }

enum class ParseStatus { PENDING_PARSE, PARSED, FAILED }

enum class Direction { INCOME, EXPENSE }

enum class MoneySource { CASH, MOMO, CARD, UNKNOWN }

enum class ContributionStatus { PENDING, PAID, MISSED }
