package sh.gravital.shell.session

import com.google.gson.annotations.SerializedName

enum class SessionPolicy { Ephemeral, Persistent, Snapshot }

enum class SessionState { Stopped, Running, Suspended }

enum class Distro { Alpine, Debian }

data class SessionModel(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("root") val root: String,
    @SerializedName("policy") val policy: SessionPolicy,
    @SerializedName("state") val state: SessionState,
    @SerializedName("distro") val distro: Distro,
    @SerializedName("created_at") val createdAt: Long,
    @SerializedName("tags") val tags: List<String> = emptyList(),
)
