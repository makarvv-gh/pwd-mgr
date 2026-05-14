package com.example.myklyuchik2.data.model

import java.util.Date
import java.util.UUID

data class PasswordEntry(
	val id: String = UUID.randomUUID().toString(),
	val resourceName: String,
	val login: String,
	val password: String,
	val changeDate: String = Date().toString(),
	val url: String = "",
	val email: String = "",
	val authCode: String = "",
	val notes: String = "",
	val tags: List<String> = emptyList(),
	val createdAt: String = Date().toString(),
	val updatedAt: String = Date().toString()
) {
	fun toDict(): Map<String, Any?> = mapOf(
		"id" to id,
		"resource_name" to resourceName,
		"login" to login,
		"password" to password,
		"change_date" to changeDate,
		"url" to url,
		"email" to email,
		"auth_code" to authCode,
		"notes" to notes,
		"tags" to tags,
		"created_at" to createdAt,
		"updated_at" to updatedAt
	)

	companion object {
		fun fromDict(map: Map<String, Any?>): PasswordEntry {
			@Suppress("UNCHECKED_CAST")
			val tagsList = (map["tags"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
			return PasswordEntry(
				id = map["id"] as? String ?: UUID.randomUUID().toString(),
				resourceName = map["resource_name"] as? String ?: "",
				login = map["login"] as? String ?: "",
				password = map["password"] as? String ?: "",
				changeDate = map["change_date"] as? String ?: Date().toString(),
				url = map["url"] as? String ?: "",
				email = map["email"] as? String ?: "",
				authCode = map["auth_code"] as? String ?: "",
				notes = map["notes"] as? String ?: "",
				tags = tagsList,
				createdAt = map["created_at"] as? String ?: Date().toString(),
				updatedAt = map["updated_at"] as? String ?: Date().toString()
			)
		}
	}
}