import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router'
import { apiFetch } from '../api/client'
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
    const [reloadKey, setReloadKey] = useState(0)

    useEffect(() => {
        async function loadSeats() {
            try {
                const response = await apiFetch(`/api/showtimes/${showtimeId}/seats`)
                if (response.status === 401) {
                    navigate('/login', { state: { from: `/showtimes/${showtimeId}/seats` } })
                    return
                }
                if (!response.ok) {
                    setError('좌석 정보를 불러오지 못했어요')
                    return
                }
                const data = await response.json()
                setSeats(data)
                // 다시 불러왔을 때 그사이 다른 사람이 잡은 좌석은 선택에서 뺀다
                setSelectedIds(ids => ids.filter(id => data.some(seat => seat.seatId === id && seat.status === 'AVAILABLE')))
            } catch {
                setError('서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.')
            }
        }
        loadSeats()
    }, [showtimeId, navigate, reloadKey])

    useEffect(() => {
        if (selectedIds.length === 0) {
            return
        }
        // 좌석을 빠르게 바꾸면 이전 선택의 응답이 늦게 올 수 있어서, 최신 요청이 아닌 응답은 버린다
        let ignore = false
        async function loadPrice() {
            try {
                const response = await apiFetch(
                    `/api/showtimes/${showtimeId}/seats/price?seatIds=${selectedIds.join(',')}`,
                )
                if (response.ok) {
                    const data = await response.json()
                    if (!ignore) {
                        setTotalPrice(data.totalPrice)
                    }
                }
            } catch {
                // 가격 표시는 참고용이고 결제 화면에서 서버 가격을 다시 확인한다
            }
        }
        loadPrice()
        return () => {
            ignore = true
        }
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
        let response
        try {
            response = await apiFetch(`/api/showtimes/${showtimeId}/seats/hold`, {
                method: 'POST',
                body: { seatIds: selectedIds },
            })
        } catch {
            setHoldMessage('서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.')
            return
        } finally {
            setHolding(false)
        }

        if (response.status === 401) {
            navigate('/login', { state: { from: `/showtimes/${showtimeId}/seats` } })
            return
        }
        if (!response.ok) {
            // 다른 사람이 먼저 잡은 좌석이 있으면 좌석판이 옛날 상태라서, 최신 현황을 다시 불러온다
            setHoldMessage(`${await response.text()} 좌석 현황을 다시 불러왔어요.`)
            setReloadKey(key => key + 1)
            return
        }

        // 서버가 알려준 남은 초를 여기서 마감 시각으로 바꿔 넘긴다. 결제 화면을 새로고침해도 처음부터 다시 세지 않는다
        const { expiresInSeconds } = await response.json()
        const selectedSeats = seats.filter(seat => selectedIds.includes(seat.seatId))
        navigate('/payment', {
            state: {
                showtimeId: Number(showtimeId),
                seatIds: selectedIds,
                seatLabels: selectedSeats.map(seat => seat.rowLabel + seat.seatNumber),
                holdExpiresAt: Date.now() + expiresInSeconds * 1000,
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
