package sh.gravital.shell.session

sealed class SessionUiState {
    object Loading : SessionUiState()
    data class Ready(val sessions: List<SessionModel>) : SessionUiState()
    data class Error(val message: String) : SessionUiState()
}
