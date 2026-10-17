function openDateSearch() { document.getElementById('chatMenu').classList.remove('show'); document.getElementById('calendarModal').classList.add('active'); renderCalendar(calCurrentDate); }

function renderCalendar(date) {
    const year = date.getFullYear(); const month = date.getMonth();
    document.getElementById('calMonthYear').innerText = new Intl.DateTimeFormat('en-US', { month: 'long', year: 'numeric' }).format(date);
    const firstDay = new Date(year, month, 1).getDay(); const daysInMonth = new Date(year, month + 1, 0).getDate();
    const grid = document.getElementById('calDatesGrid'); grid.innerHTML = '';
    for(let i=0; i<firstDay; i++) grid.innerHTML += `<div class="cal-date empty"></div>`;
    for(let d=1; d<=daysInMonth; d++) {
        const dayEl = document.createElement('div'); dayEl.className = 'cal-date ripple-surface'; dayEl.innerText = d;
        if(calSelectedDate && calSelectedDate.getDate() === d && calSelectedDate.getMonth() === month && calSelectedDate.getFullYear() === year) dayEl.classList.add('active');
        dayEl.onclick = () => selectDate(new Date(year, month, d)); grid.appendChild(dayEl);
    }
}

function changeCalMonth(delta) { calCurrentDate.setMonth(calCurrentDate.getMonth() + delta); renderCalendar(calCurrentDate); }
function selectDate(date) { calSelectedDate = date; document.getElementById('calSelectedDateStr').innerText = date.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' }); renderCalendar(calCurrentDate); }

function jumpToDate() {
    if(!currentMessages.length) { closeModal('calendarModal'); return; }
    const targetTs = calSelectedDate.setHours(0,0,0,0) / 1000;
    const msg = currentMessages.find(m => m.timestamp >= targetTs);
    closeModal('calendarModal');
    if(msg) {
        const el = document.getElementById(`msg-${msg.timestamp}`);
        if(el) { el.scrollIntoView({behavior: 'smooth', block: 'center'}); el.style.backgroundColor = 'var(--search-highlight)'; setTimeout(() => el.style.backgroundColor = '', 2000); }
    } else document.getElementById('notFoundModal').classList.add('active');
}
