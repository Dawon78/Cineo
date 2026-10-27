import { useState, useEffect } from 'react'
import { useLocation, useNavigate, Navigate, Link } from 'react-router'
import { clearTokens } from '../auth'

function authHeaders() {
    return {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${localStorage.getItem('accessToken')}`,
    }
}

function PaymentPage() {
    const location = useLocation()
    const navigate = useNavigate()
    const order = location.state
    const [amount, setAmount] = useState(null)
    const [paying, setPaying] = useState(false)
    const [message, setMessage] = useState('')
    const [reservation, setReservation] = useState(null)

    useEffect(() => {
        if (!order) {
            return
        }
        async function loadPrice() {
            const response = await fetch(
                `http://localhost:8082/api/showtimes/${order.showtimeId}/seats/price?seatIds=${order.seatIds.join(',')}`,
                { headers: authHeaders() },
            )
            if (response.status === 401) {
                clearTokens()
                navigate('/login')
                return
            }
            if (!response.ok) {
                setMessage('결제 금액을 불러오지 못했어요')
                return
            }
            const data = await response.json()
            setAmount(data.totalPrice)
        }
        loadPrice()
    }, [order, navigate])

    if (!order) {
        return <Navigate to="/" replace />
    }

    async function confirmReservation(impUid) {
        try {
            const response = await fetch('http://localhost:8082/api/reservations', {
                method: 'POST',
                headers: authHeaders(),
                body: JSON.stringify({ showtimeId: order.showtimeId, seatIds: order.seatIds, impUid }),
            })
            if (!response.ok) {
                setMessage(await response.text())
                return
            }
            setReservation(await response.json())
        } catch {
            setMessage(`결제는 완료됐지만 예매 확정 요청에 실패했어요. 결제번호(${impUid})를 보관해 주세요.`)
        } finally {
            setPaying(false)
        }
    }

    function handlePay() {
        const impCode = import.meta.env.VITE_PORTONE_IMP_CODE
        if (!window.IMP || !impCode) {
            setMessage('결제 모듈 설정이 없어요. frontend/.env.local 을 확인해 주세요.')
            return
        }

        setPaying(true)
        setMessage('')
        window.IMP.init(impCode)

        const params = {
            pay_method: 'card',
            merchant_uid: `cineo_${Date.now()}`,
            name: `Cineo 영화 예매 (${order.seatLabels.length}석)`,
            amount,
            buyer_name: 'Cineo 회원',
            buyer_tel: '01000000000',
        }
        const channelKey = import.meta.env.VITE_PORTONE_CHANNEL_KEY
        if (channelKey) {
            params.channelKey = channelKey
        }

        window.IMP.request_pay(params, (rsp) => {
            if (!rsp.success) {
                setPaying(false)
                setMessage(`결제가 완료되지 않았어요: ${rsp.error_msg}`)
                return
            }
            confirmReservation(rsp.imp_uid)
        })
    }

    async function handleCancel() {
        await fetch(`http://localhost:8082/api/showtimes/${order.showtimeId}/seats/hold`, {
            method: 'DELETE',
            headers: authHeaders(),
            body: JSON.stringify({ seatIds: order.seatIds }),
        })
        navigate(`/showtimes/${order.showtimeId}/seats`)
    }

    if (reservation) {
        return (
            <section>
                <h1 className="page-title">예매가 완료됐어요</h1>
                <div className="ticket">
                    <div className="ticket-main">
                        <p className="ticket-movie">{reservation.movieTitle}</p>
                        <dl>
                            <div className="ticket-row">
                                <dt>일시</dt>
                                <dd>
                                    {new Date(reservation.startTime).toLocaleString('ko-KR', {
                                        month: 'long', day: 'numeric', weekday: 'short', hour: '2-digit', minute: '2-digit',
                                    })}
                                </dd>
                            </div>
                            <div className="ticket-row">
                                <dt>상영관</dt>
                                <dd>{reservation.screenName}</dd>
                            </div>
                            <div className="ticket-row">
                                <dt>좌석</dt>
                                <dd>{order.seatLabels.join(', ')}</dd>
                            </div>
                        </dl>
                    </div>
                    <div className="ticket-stub">
                        <div className="ticket-total">
                            <span>결제 금액</span>
                            <strong>{reservation.totalPrice.toLocaleString()}원</strong>
                        </div>
                        <Link to="/" className="btn">다른 영화 보기</Link>
                    </div>
                </div>
            </section>
        )
    }

    return (
        <section>
            <h1 className="page-title">결제</h1>
            <div className="ticket">
                <div className="ticket-main">
                    <dl>
                        <div className="ticket-row">
                            <dt>좌석</dt>
                            <dd>{order.seatLabels.join(', ')}</dd>
                        </div>
                        <div className="ticket-row">
                            <dt>인원</dt>
                            <dd>{order.seatLabels.length}명</dd>
                        </div>
                    </dl>
                </div>
                <div className="ticket-stub">
                    <div className="ticket-total">
                        <span>결제 금액</span>
                        <strong>{amount === null ? '계산 중...' : `${amount.toLocaleString()}원`}</strong>
                    </div>
                    <p className="ticket-hint">좌석은 5분 동안 잡아 둬요. 그 안에 결제를 마쳐 주세요.</p>
                    <div className="ticket-actions">
                        <button className="btn" onClick={handleCancel} disabled={paying}>좌석 다시 고르기</button>
                        <button className="btn btn-primary" onClick={handlePay} disabled={amount === null || paying}>
                            {paying ? '결제 진행 중...' : '결제하기'}
                        </button>
                    </div>
                    {message && <p className="ticket-message">{message}</p>}
                </div>
            </div>
        </section>
    )
}

export default PaymentPage
