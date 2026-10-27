import { useState, useEffect } from 'react'
import { useNavigate, Link } from 'react-router'
import { clearTokens } from '../auth'

const TIER_LABELS = { BASIC: '베이직', SILVER: '실버', GOLD: '골드', VIP: 'VIP' }

function authHeaders() {
    return { Authorization: `Bearer ${localStorage.getItem('accessToken')}` }
}

function MyPage() {
    const navigate = useNavigate()
    const [member, setMember] = useState(null)
    const [reservations, setReservations] = useState([])
    const [error, setError] = useState(null)
    const [cancellingId, setCancellingId] = useState(null)
    const [reloadKey, setReloadKey] = useState(0)

    useEffect(() => {
        async function load() {
            const [memberRes, reservationRes] = await Promise.all([
                fetch('http://localhost:8082/api/members/me', { headers: authHeaders() }),
                fetch('http://localhost:8082/api/reservations/me', { headers: authHeaders() }),
            ])
            if (memberRes.status === 401 || reservationRes.status === 401) {
                clearTokens()
                navigate('/login')
                return
            }
            if (!memberRes.ok || !reservationRes.ok) {
                setError('내 정보를 불러오지 못했어요')
                return
            }
            setMember(await memberRes.json())
            const list = await reservationRes.json()
            setReservations([...list].sort((a, b) => b.createdAt.localeCompare(a.createdAt)))
        }
        load()
    }, [navigate, reloadKey])

    async function handleCancel(reservation) {
        if (!window.confirm(`${reservation.movieTitle} 예매를 취소할까요? 결제 금액은 환불돼요.`)) {
            return
        }
        setCancellingId(reservation.id)
        const response = await fetch(`http://localhost:8082/api/reservations/${reservation.id}`, {
            method: 'DELETE',
            headers: authHeaders(),
        })
        setCancellingId(null)
        if (!response.ok) {
            window.alert(await response.text())
            return
        }
        setReloadKey(key => key + 1)
    }

    if (error) {
        return <p className="notice notice-error">{error}</p>
    }

    if (member === null) {
        return <p className="notice">불러오는 중...</p>
    }

    const now = new Date()

    return (
        <section>
            <h1 className="page-title">마이페이지</h1>

            <div className="member-summary">
                <div>
                    <p className="member-name">{member.name || member.email}</p>
                    <p className="member-email">{member.email}</p>
                </div>
                <dl className="member-stats">
                    <div>
                        <dt>등급</dt>
                        <dd>{TIER_LABELS[member.tier] ?? member.tier}</dd>
                    </div>
                    <div>
                        <dt>적립금</dt>
                        <dd>{member.points.toLocaleString()}원</dd>
                    </div>
                    <div>
                        <dt>누적 결제</dt>
                        <dd>{member.totalSpent.toLocaleString()}원</dd>
                    </div>
                </dl>
            </div>

            <h2 className="section-title">예매 내역</h2>
            {reservations.length === 0 && (
                <p className="notice">
                    아직 예매한 영화가 없어요. <Link to="/">상영 중인 영화 보기</Link>
                </p>
            )}
            <ul className="reservation-list">
                {reservations.map(reservation => {
                    const cancelled = reservation.status === 'CANCELLED'
                    const started = new Date(reservation.startTime) <= now
                    return (
                        <li key={reservation.id} className={cancelled ? 'reservation-item is-cancelled' : 'reservation-item'}>
                            <div>
                                <p className="reservation-movie">{reservation.movieTitle}</p>
                                <p className="reservation-meta">
                                    {new Date(reservation.startTime).toLocaleString('ko-KR', {
                                        month: 'long', day: 'numeric', weekday: 'short', hour: '2-digit', minute: '2-digit',
                                    })}
                                    {' '}{reservation.screenName}
                                </p>
                            </div>
                            <div className="reservation-side">
                                <p className="reservation-price">{reservation.totalPrice.toLocaleString()}원</p>
                                {cancelled && <span className="status-badge">취소됨</span>}
                                {!cancelled && started && <span className="status-badge">상영 시작됨</span>}
                                {!cancelled && !started && (
                                    <button
                                        type="button"
                                        className="btn"
                                        onClick={() => handleCancel(reservation)}
                                        disabled={cancellingId === reservation.id}
                                    >
                                        {cancellingId === reservation.id ? '취소하는 중...' : '예매 취소'}
                                    </button>
                                )}
                            </div>
                        </li>
                    )
                })}
            </ul>
        </section>
    )
}

export default MyPage
