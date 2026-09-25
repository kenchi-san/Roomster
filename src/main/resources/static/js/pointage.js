document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('tbody tr[data-id]').forEach(row => {
        const bouton = row.querySelector('.btn-pointer');
        if (bouton) {
            bouton.addEventListener('click', () => pointer(row.dataset.id));
        }

        const btnEdit = row.querySelector('.btn-edit-pointage');
        if (btnEdit) {
            const id = row.dataset.id;
            btnEdit.addEventListener('click', () => setEditModePointage(row, true));
            row.querySelector('.btn-cancel-pointage')
                .addEventListener('click', () => {
                    resetPointageFields(row);
                    hidePointageError(row);
                    setEditModePointage(row, false);
                });
            row.querySelector('.btn-save-pointage')
                .addEventListener('click', () => corrigerPointage(id, row));
        }
    });
});

function csrfHeader() {
    const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
    return match ? { 'X-XSRF-TOKEN': decodeURIComponent(match[1]) } : {};
}

function pointer(id) {
    fetch(`/pointer-employe/${id}`, { method: 'POST', headers: csrfHeader() })
        .then(response => {
            if (!response.ok) {
                return response.text().then(message => { throw new Error(message || 'pointage failed'); });
            }
            window.location.reload();
        })
        .catch(error => alert(error.message || 'Le pointage a échoué.'));
}

function setEditModePointage(row, editing) {
    row.querySelectorAll('[data-view]').forEach(el => el.classList.toggle('hidden', editing));
    row.querySelectorAll('.edit').forEach(el => el.classList.toggle('hidden', !editing));
    row.querySelector('.btn-edit-pointage').classList.toggle('hidden', editing);
    row.querySelector('.btn-save-pointage').classList.toggle('hidden', !editing);
    row.querySelector('.btn-cancel-pointage').classList.toggle('hidden', !editing);
    if (editing) {
        hidePointageError(row);
    }
}

function resetPointageFields(row) {
    row.querySelectorAll('[data-field]').forEach(field => {
        field.value = field.defaultValue;
        field.classList.remove('border-red-400');
        field.classList.add('border-slate-300');
    });
}

function showPointageError(row, message, champInvalide) {
    const erreur = row.querySelector('[data-error-pointage]');
    erreur.textContent = message;
    erreur.classList.remove('hidden');
    row.querySelectorAll('[data-field]').forEach(field => {
        field.classList.remove('border-red-400');
        field.classList.add('border-slate-300');
    });
    if (champInvalide) {
        const champ = row.querySelector(`[data-field="${champInvalide}"]`);
        champ.classList.remove('border-slate-300');
        champ.classList.add('border-red-400');
    }
}

function hidePointageError(row) {
    const erreur = row.querySelector('[data-error-pointage]');
    erreur.classList.add('hidden');
    row.querySelectorAll('[data-field]').forEach(field => {
        field.classList.remove('border-red-400');
        field.classList.add('border-slate-300');
    });
}

function corrigerPointage(id, row) {
    const dto = {};
    row.querySelectorAll('[data-field]').forEach(field => {
        dto[field.dataset.field] = field.value || null;
    });

    if (!dto.commentaire) {
        showPointageError(row, 'Un commentaire est obligatoire pour corriger un pointage.', 'commentaire');
        return;
    }
    if (dto.sortie && dto.sortie <= dto.entree) {
        showPointageError(row, 'La sortie doit être postérieure à l\'entrée.', 'sortie');
        return;
    }
    hidePointageError(row);

    fetch(`/pointer-employe/${id}/corriger`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', ...csrfHeader() },
        body: JSON.stringify(dto)
    })
        .then(response => {
            if (!response.ok) {
                return response.text().then(message => { throw new Error(message || 'La correction a échoué.'); });
            }
            return response.json();
        })
        .then(updated => {
            row.querySelector('[data-view="entree"]').textContent = formatDateHeure(updated.entree);
            row.querySelector('[data-view="sortie"]').textContent = updated.sortie ? formatDateHeure(updated.sortie) : '—';
            row.querySelector('[data-view="commentaire"]').textContent = updated.commentaire || '—';
            row.querySelectorAll('[data-field]').forEach(field => {
                field.defaultValue = field.value;
            });
            setEditModePointage(row, false);
        })
        .catch(error => showPointageError(row, error.message || 'La correction a échoué.'));
}

function formatDateHeure(iso) {
    const date = new Date(iso);
    const pad = n => String(n).padStart(2, '0');
    return `${pad(date.getDate())}/${pad(date.getMonth() + 1)}/${date.getFullYear()} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
