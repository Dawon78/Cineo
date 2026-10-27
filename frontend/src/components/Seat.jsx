function Seat({ seat, selected, onToggle }) {
  const available = seat.status === 'AVAILABLE'

  return (
    <button
      onClick={() => onToggle(seat.seatId)}
      disabled={!available}
      style={{
        width: 36,
        height: 36,
        margin: 2,
        background: selected ? '#e50914' : available ? '#eee' : '#555',
        color: selected ? 'white' : 'black',
      }}
    >
      {seat.seatNumber}
    </button>
  )
}

export default Seat
