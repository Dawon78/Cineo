import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams, Link } from 'react-router'
import { apiFetch } from '../api/client'

// 모바일 결제창이 끝나면 PortOne이 결과를 주소 뒤에 붙여 이 화면으로 돌려보낸다 (?imp_uid=...&imp_success=true)
function PaymentCompletePage() {
    const [searchParams] = useSearchParams()
    const navigate = useNavigate()
    const impUid = searchParams.get('imp_uid')
    const failed = !impUid || searchParams.get('imp_success') === 'false' || searchParams.has('error_code')
    // 처음 그릴 때 한 번만 꺼내 둔다. 아래 effect에서 저장소를 비워도 이 값은 남는다
    const [savedOrder] = useState(() => sessionStorage.getItem('pendingOrder'))
    const [message, setMessage] = useState('결제 결과를 확인하고 있어요...')
    const started = useRef(false)

    useEffect(() => {
        // 새로고침해서 다시 열려도 같은 주문으로 또 확정하지 않게 바로 지운다
        sessionStorage.removeItem('pendingOrder')
        // 개발 모드(StrictMode)는 effect를 두 번 실행해서, 같은 결제로 예매 확정을 두 번 요청하지 않게 막는다
        if (failed || !savedOrder || started.current) {
            return
        }
        started.current = true
        const order = JSON.parse(savedOrder)

        async function confirmReservation() {
            try {
                const response = await apiFetch('/api/reservations', {
                    method: 'POST',
                    body: { showtimeId: order.showtimeId, seatIds: order.seatIds, impUid },
                })
                if (!response.ok) {
                    setMessage(await response.text())
                    return
                }
                const saved = await response.json()
                navigate(`/reservations/${saved.id}`, { replace: true, state: { justPaid: true } })
            } catch {
                setMessage(`결제는 완료됐지만 예매 확정 요청에 실패했어요. 결제번호(${impUid})를 보관해 주세요.`)
            }
        }
        confirmReservation()
    }, [failed, savedOrder, impUid, navigate])

    function getMessage() {
        if (failed) {
            return `결제가 완료되지 않았어요: ${searchParams.get('error_msg') ?? '알 수 없는 오류'}`
        }
        if (!savedOrder) {
            return `주문 정보를 찾을 수 없어요. 결제번호(${impUid})를 보관해 주세요.`
        }
        return message
    }

    return (
        <section>
            <h1 className="page-title">결제 확인</h1>
            <p className="ticket-message">{getMessage()}</p>
            <Link to="/mypage">마이페이지로 가기</Link>
        </section>
    )
}

export default PaymentCompletePage