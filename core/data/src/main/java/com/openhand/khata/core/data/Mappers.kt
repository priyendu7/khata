package com.openhand.khata.core.data

import com.openhand.khata.core.database.dao.TagUsage
import com.openhand.khata.core.database.entity.AccountEntity
import com.openhand.khata.core.database.entity.CategoryEntity
import com.openhand.khata.core.model.Account
import com.openhand.khata.core.model.Category
import com.openhand.khata.core.model.Tag

internal fun AccountEntity.toModel() = Account(id, name, type, bank, last4)

internal fun Account.toEntity() = AccountEntity(id, name, type, bank, last4)

internal fun CategoryEntity.toModel() = Category(id, name, seedKey, color, icon, archived)

internal fun Category.toEntity() = CategoryEntity(id, name, seedKey, color, icon, archived)

internal fun TagUsage.toModel() = Tag(id, name, usage)
