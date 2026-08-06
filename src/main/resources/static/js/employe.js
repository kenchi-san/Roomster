let showOnlyInactifs = false;

document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('tbody tr').forEach(row => {
        const id = row.dataset.id;

        row.querySelector('.btn-delete-employe')
            .addEventListener('click', () => deleteEmploye(id, row));

        row.querySelector('.btn-edit-employe')
            .addEventListener('click', () => setEditMode(row, true));

        row.querySelector('.btn-cancel-employe')
            .addEventListener('click', () => {
                resetFields(row);
                setEditMode(row, false);
            });

        row.querySelector('.btn-save-employe')
            .addEventListener('click', () => saveEmploye(id, row));

        row.querySelector('.btn-toggle-actif')
            .addEventListener('click', () => toggleActif(id, row));
    });

    const btnFilterInactifs = document.getElementById('btn-filter-inactifs');
    btnFilterInactifs.addEventListener('click', () => {
        showOnlyInactifs = !showOnlyInactifs;
        btnFilterInactifs.textContent = showOnlyInactifs ? 'Voir tous les employés' : 'Voir les inactifs';
        applyInactifFilter();
    });
});

function applyInactifFilter() {
    document.querySelectorAll('tbody tr').forEach(row => {
        const estActif = row.querySelector('[data-view="actif"]').textContent.trim() === 'Oui';
        row.classList.toggle('hidden', showOnlyInactifs && estActif);
    });
}

function setEditMode(row, editing) {
    row.querySelectorAll('[data-view]').forEach(el => el.classList.toggle('hidden', editing));
    row.querySelectorAll('.edit').forEach(el => {
        if (el.type !== 'hidden') {
            el.classList.toggle('hidden', !editing);
        }
    });
    row.querySelector('.btn-edit-employe').classList.toggle('hidden', editing);
    row.querySelector('.btn-delete-employe').classList.toggle('hidden', editing);
    row.querySelector('.btn-save-employe').classList.toggle('hidden', !editing);
    row.querySelector('.btn-cancel-employe').classList.toggle('hidden', !editing);
}

function resetFields(row) {
    row.querySelectorAll('[data-field]').forEach(field => {
        const view = row.querySelector(`[data-view="${field.dataset.field}"]`);
        if (!view) {
            return;
        }
        if (field.type === 'checkbox') {
            field.checked = view.textContent.trim() === 'Oui';
        } else {
            field.value = view.textContent.trim();
        }
    });
}

function deleteEmploye(id, row) {
    if (!confirm('Voulez-vous vraiment supprimer cet employé ?')) {
        return;
    }

    fetch(`/delete-employe/${id}`, { method: 'DELETE' })
        .then(response => {
            if (response.status === 204) {
                row.remove();
            } else if (response.status === 404) {
                alert("Cet employé n'existe plus.");
            } else {
                alert('La suppression a échoué.');
            }
        })
        .catch(() => alert('Erreur réseau : la suppression a échoué.'));
}

function saveEmploye(id, row) {
    const dto = {};
    row.querySelectorAll('[data-field]').forEach(field => {
        dto[field.dataset.field] = field.type === 'checkbox' ? field.checked : (field.value || null);
    });

    fetch(`/edit-employe/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(dto)
    })
        .then(response => {
            if (!response.ok) {
                return response.text().then(message => { throw new Error(message || 'update failed'); });
            }
            return response.json();
        })
        .then(updated => {
            row.querySelector('[data-view="nom"]').textContent = updated.nom;
            row.querySelector('[data-view="prenom"]').textContent = updated.prenom;
            row.querySelector('[data-view="email"]').textContent = updated.email;
            row.querySelector('[data-view="poste"]').textContent = updated.poste;
            row.querySelector('[data-view="typeContrat"]').textContent = updated.typeContrat;
            row.querySelector('[data-view="dateEntree"]').textContent = updated.dateEntree;
            updateActifDisplay(row, updated.actif);
            updateDateSortie(row, updated.dateSortie);
            setEditMode(row, false);
        })
        .catch(error => alert(error.message || 'La mise à jour a échoué.'));
}

function toggleActif(id, row) {
    fetch(`/toggle-actif-employe/${id}`, { method: 'PATCH' })
        .then(response => {
            if (!response.ok) {
                return response.text().then(message => { throw new Error(message || 'toggle failed'); });
            }
            return response.json();
        })
        .then(updated => {
            updateActifDisplay(row, updated.actif);
            updateDateSortie(row, updated.dateSortie);
            applyInactifFilter();
        })
        .catch(error => alert(error.message || 'Le changement de statut a échoué.'));
}

function updateDateSortie(row, dateSortie) {
    row.querySelector('[data-field="dateSortie"]').value = dateSortie || '';
    const dateEntreeInput = row.querySelector('[data-field="dateEntree"]');
    if (dateSortie) {
        dateEntreeInput.max = dateSortie;
    } else {
        dateEntreeInput.removeAttribute('max');
    }
}

function updateActifDisplay(row, actif) {
    const badge = row.querySelector('[data-view="actif"]');
    badge.textContent = actif ? 'Oui' : 'Non';
    badge.className = actif
        ? 'inline-flex rounded-full bg-green-100 px-2 py-0.5 text-xs font-medium text-green-700'
        : 'inline-flex rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600';
    row.querySelector('.btn-toggle-actif').textContent = actif ? 'Désactiver' : 'Activer';
}
