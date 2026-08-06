document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('tbody tr[data-id]').forEach(row => {
        const bouton = row.querySelector('.btn-pointer');
        if (bouton) {
            bouton.addEventListener('click', () => pointer(row.dataset.id));
        }
    });
});

function pointer(id) {
    fetch(`/pointer-employe/${id}`, { method: 'POST' })
        .then(response => {
            if (!response.ok) {
                return response.text().then(message => { throw new Error(message || 'pointage failed'); });
            }
            window.location.reload();
        })
        .catch(error => alert(error.message || 'Le pointage a échoué.'));
}
