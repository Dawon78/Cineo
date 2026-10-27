import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router'
import { clearTokens } from '../auth'
import Seat from '../components/Seat'

const MAX_SEATS = 10

function SeatPage() {
    const { showtimeId } = useParams()
    const navigate = useNavigate()
    const [seats, setSeats] = useState([])
    const [selectedIds, setSelectedIds] = useState([])
    const [totalPrice, setTotalPrice] = useState(0)
    const [error, setError] = useState(null)
    const [holdMessage, setHoldMessage] = useState('')
    const [holding, setHolding] = useState(false)

    useEffect(() => {
        async function loadSeats() {
            const token = localStorage.getItem('accessToken')
            const response = await fetch(`http://localhost:8082/api/showtimes/${showtimeId}/seats`, {
                headers: { Authorization: `Bearer ${token}` },
            })
            if (response.status === 401) {
                clearTokens()
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

    async function handleReserve() {
        setHolding(true)
        setHoldMessage('')
        const token = localStorage.getItem('accessToken')
        const response = await fetch(`http://localhost:8082/api/showtimes/${showtimeId}/seats/hold`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
            body: JSON.stringify({ seatIds: selectedIds }),
        })
        setHolding(false)

        if (response.status === 401) {
            clearTokens()
            navigate('/login')
            return
        }
        if (!response.ok) {
            setHoldMessage(await response.text())
            return
        }

        const selectedSeats = seats.filter(seat => selectedIds.includes(seat.seatId))
        navigate('/payment', {
            state: {
                showtimeId: Number(showtimeId),
                seatIds: selectedIds,
                seatLabels: selectedSeats.map(seat => seat.rowLabel + seat.seatNumber),
            },
        })
    }

    if (error) {
        return <p className="notice notice-error">{error}</p>
    }

    const rows = [...new Set(seats.map(seat => seat.rowLabel))]
    const selectedLabels = seats
        .filter(seat => selectedIds.includes(seat.seatId))
        .map(seat => seat.rowLabel + seat.seatNumber)

    return (
        <section className="seat-page">
            <h1 className="page-title">좌석 선택</h1>
            <div className="screen">SCREEN</div>

            <div className="seat-map">
                {rows.map(row => (
                    <div key={row} className="seat-row">
                        <span className="row-label">{row}</span>
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
            </div>

            <div className="seat-legend">
                <span><i />선택 가능</span>
                <span><i className="legend-selected" />선택한 좌석</span>
                <span><i className="legend-taken" />예매 불가</span>
            </div>

            <div className="summary-bar">
                <div>
                    <p className="summary-count">
                        {selectedIds.length === 0
                            ? `좌석을 골라 주세요 (최대 ${MAX_SEATS}석)`
                            : `${selectedLabels.join(', ')} · ${selectedIds.length}석`}
                    </p>
                    {selectedIds.length > 0 && <p className="summary-price">{totalPrice.toLocaleString()}원</p>}
                    {holdMessage && <p className="summary-error">{holdMessage}</p>}
                </div>
                <button
                    className="btn btn-primary"
                    onClick={handleReserve}
                    disabled={selectedIds.length === 0 || holding}
                >
                    {holding ? '좌석 확보 중...' : '예매하기'}
                </button>
            </div>
        </section>
    )
}

export default SeatPage
