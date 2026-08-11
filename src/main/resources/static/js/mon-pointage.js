function csrfHeader() {
    const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
    return match ? { 'X-XSRF-TOKEN': decodeURIComponent(match[1]) } : {};
}

document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('btn-pointer').addEventListener('click', () => {
        fetch('/mon-pointage/pointer', { method: 'POST', headers: csrfHeader() })
            .then(response => {
                if (!response.ok) {
                    return response.text().then(message => { throw new Error(message || 'pointage failed'); });
                }
                window.location.reload();
            })
            .catch(error => alert(error.message || 'Le pointage a échoué.'));
    });
});
