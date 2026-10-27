import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router'
import Seat from '../components/Seat'

const MAX_SEATS = 10

function SeatPage() {
    const { showtimeId } = useParams()
    const navigate = useNavigate()
    const [seats, setSeats] = useState([])
    const [selectedIds, setSelectedIds] = useState([])
    const [totalPrice, setTotalPrice] = useState(0)
    const [error, setError] = useState(null)

    useEffect(() => {
        async function loadSeats() {
            const token = localStorage.getItem('accessToken')
            const response = await fetch(`http://localhost:8082/api/showtimes/${showtimeId}/seats`, {
                headers: { Authorization: `Bearer ${token}` },
            })
            if (response.status === 401) {
                navigate('/login')
                return
            }
            if (!response.ok) {
                setError('좌석 정보를 불러오지 못했어요')
                return
            }
            setSeats(await response.json())
        }
        loadSeats()
    }, [showtimeId, navigate])

    useEffect(() => {
        if (selectedIds.length === 0) {
            return
        }
        async function loadPrice() {
            const token = localStorage.getItem('accessToken')
            const response = await fetch(
                `http://localhost:8082/api/showtimes/${showtimeId}/seats/price?seatIds=${selectedIds.join(',')}`,
                { headers: { Authorization: `Bearer ${token}` } },
            )
            if (response.ok) {
                const data = await response.json()
                setTotalPrice(data.totalPrice)
            }
        }
        loadPrice()
    }, [showtimeId, selectedIds])

    function toggleSeat(seatId) {
        if (selectedIds.includes(seatId)) {
            setSelectedIds(selectedIds.filter(id => id !== seatId))
        } else if (selectedIds.length < MAX_SEATS) {
            setSelectedIds([...selectedIds, seatId])
        }
    }

    if (error) {
        return <p>{error}</p>
    }

    const rows = [...new Set(seats.map(seat => seat.rowLabel))]

    return (
        <div>
            <h2>좌석 선택</h2>
            <p>SCREEN</p>
            {rows.map(row => (
                <div key={row}>
                    <span style={{ display: 'inline-block', width: 20 }}>{row}</span>
                    {seats
                        .filter(seat => seat.rowLabel === row)
                        .map(seat => (
                            <Seat
                                key={seat.seatId}
                                seat={seat}
                                selected={selectedIds.includes(seat.seatId)}
                                onToggle={toggleSeat}
                            />
                        ))}
                </div>
            ))}

            <p>선택한 좌석: {selectedIds.length}석 (최대 {MAX_SEATS}석)</p>
            {selectedIds.length > 0 && <p>총 {totalPrice.toLocaleString()}원</p>}
        </div>
    )
}

export default SeatPage
