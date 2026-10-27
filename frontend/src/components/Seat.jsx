function Seat({ seat, selected, onToggle }) {
  const available = seat.status === 'AVAILABLE'
  const label = seat.rowLabel + seat.seatNumber

  return (
    <button
      type="button"
      className={selected ? 'seat is-selected' : 'seat'}
      onClick={() => onToggle(seat.seatId)}
      disabled={!available}
      aria-pressed={selected}
      aria-label={available ? `${label} 좌석` : `${label} 좌석, 예매 불가`}
    >
      {seat.seatNumber}
    </button>
  )
}

export default Seat
