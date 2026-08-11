document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('tbody tr[data-id]').forEach(row => {
        const bouton = row.querySelector('.btn-pointer');
        if (bouton) {
            bouton.addEventListener('click', () => pointer(row.dataset.id));
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
