import { useState, useEffect, useRef } from 'react'
import { useParams, useLocation, useNavigate, Link } from 'react-router'
import { apiFetch } from '../api/client'

const GRADE_LABELS = { STANDARD: '일반', PREMIUM: '프리미엄', COUPLE: '커플' }

function formatDateTime(dateTime) {
    return new Date(dateTime).toLocaleString('ko-KR', {
        year: 'numeric', month: 'long', day: 'numeric', weekday: 'short', hour: '2-digit', minute: '2-digit',
    })
}

function formatTime(dateTime) {
    return new Date(dateTime).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false })
}

function ReservationDetailPage() {
    const { reservationId } = useParams()
    const location = useLocation()
    const navigate = useNavigate()
    const [reservation, setReservation] = useState(null)
    const [error, setError] = useState(null)
    const dialogRef = useRef(null)
    // 결제 화면에서 막 넘어왔을 때만 완료 창을 띄운다. 마이페이지에서 들어오면 안 뜬다
    const justPaid = location.state?.justPaid === true

    useEffect(() => {
        async function load() {
            try {
                const response = await apiFetch(`/api/reservations/${reservationId}`)
                if (response.status === 401) {
                    navigate('/login', { state: { from: `/reservations/${reservationId}` } })
                    return
                }
                if (!response.ok) {
                    setError(response.status === 404 || response.status === 403
                        ? '예매 내역을 찾을 수 없어요'
                        : '예매 내역을 불러오지 못했어요')
                    return
                }
                setReservation(await response.json())
            } catch {
                setError('서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.')
            }
        }
        load()
    }, [reservationId, navigate])

    useEffect(() => {
        if (justPaid && reservation && !dialogRef.current.open) {
            dialogRef.current.showModal()
        }
    }, [justPaid, reservation])

    function handleDialogClose() {
        // 표시 여부를 방문 기록에서 지워서, 새로고침해도 완료 창이 다시 뜨지 않게 한다
        navigate(location.pathname, { replace: true, state: null })
    }

    if (error) {
        return <p className="notice notice-error">{error}</p>
    }

    if (reservation === null) {
        return <p className="notice">불러오는 중...</p>
    }

    const cancelled = reservation.status === 'CANCELLED'

    return (
        <section>
            <h1 className="page-title">예매 상세</h1>

            <div className={cancelled ? 'ticket is-cancelled' : 'ticket'}>
                <div className="ticket-main">
                    <p className="ticket-movie">{reservation.movieTitle}</p>
                    <dl>
                        <div className="ticket-row">
                            <dt>일시</dt>
                            <dd>{formatDateTime(reservation.startTime)} ~ {formatTime(reservation.endTime)}</dd>
                        </div>
                        <div className="ticket-row">
                            <dt>상영관</dt>
                            <dd>{reservation.screenName}</dd>
                        </div>
                        <div className="ticket-row">
                            <dt>좌석</dt>
                            <dd>{reservation.seats.map(seat => seat.label).join(', ')}</dd>
                        </div>
                        <div className="ticket-row">
                            <dt>상태</dt>
                            <dd>{cancelled ? '취소됨' : '예매 완료'}</dd>
                        </div>
                    </dl>
                </div>
                <div className="ticket-stub">
                    <h2 className="ticket-section-title">결제 정보</h2>
                    <dl>
                        {reservation.seats.map(seat => (
                            <div key={seat.label} className="ticket-row">
                                <dt>{seat.label} ({GRADE_LABELS[seat.grade] ?? seat.grade})</dt>
                                <dd>{seat.price.toLocaleString()}원</dd>
                            </div>
                        ))}
                        <div className="ticket-row ticket-row-total">
                            <dt>결제 금액</dt>
                            <dd>{(reservation.paidAmount ?? reservation.totalPrice).toLocaleString()}원</dd>
                        </div>
                        {reservation.earnedPoints != null && (
                            <div className="ticket-row">
                                <dt>적립 포인트</dt>
                                <dd>{reservation.earnedPoints.toLocaleString()}원</dd>
                            </div>
                        )}
                        <div className="ticket-row">
                            <dt>결제 일시</dt>
                            <dd>{formatDateTime(reservation.createdAt)}</dd>
                        </div>
                        {reservation.impUid && (
                            <div className="ticket-row">
                                <dt>결제번호</dt>
                                <dd className="ticket-code">{reservation.impUid}</dd>
                            </div>
                        )}
                        {cancelled && reservation.canceledAt && (
                            <div className="ticket-row">
                                <dt>취소 일시</dt>
                                <dd>{formatDateTime(reservation.canceledAt)}</dd>
                            </div>
                        )}
                    </dl>
                    <div className="ticket-actions">
                        <Link to="/mypage" className="btn">예매 내역</Link>
                        <Link to="/" className="btn btn-primary">다른 영화 보기</Link>
                    </div>
                </div>
            </div>

            <dialog ref={dialogRef} className="paid-dialog" onClose={handleDialogClose} aria-labelledby="paid-dialog-title">
                <p id="paid-dialog-title" className="paid-dialog-title">결제가 완료되었습니다</p>
                <p className="paid-dialog-text">
                    {reservation.movieTitle} · {reservation.seats.length}석 · {(reservation.paidAmount ?? reservation.totalPrice).toLocaleString()}원
                </p>
                <form method="dialog">
                    <button className="btn btn-primary" autoFocus>확인</button>
                </form>
            </dialog>
        </section>
    )
}

export default ReservationDetailPage
