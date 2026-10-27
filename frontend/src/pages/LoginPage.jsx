import { useState } from 'react'
import { useNavigate, useLocation, Link } from 'react-router'
import { apiFetch } from '../api/client'
import { saveTokens } from '../auth'

function LoginPage() {
    const location = useLocation()
    const signedUpEmail = location.state?.signedUpEmail
    const [email, setEmail] = useState(signedUpEmail ?? '')
    const [password, setPassword] = useState('')
    const [message, setMessage] = useState('')
    const navigate = useNavigate()

    async function handleSubmit(e) {
        e.preventDefault()

        let response
        try {
            response = await apiFetch('/api/auth/login', {
                method: 'POST',
                body: { email, password },
            })
        } catch {
            setMessage('서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.')
            return
        }

        if (!response.ok) {
            const errorText = await response.text()
            setMessage(errorText)
            return
        }

        const data = await response.json()
        saveTokens(data.accessToken, data.refreshToken)
        navigate('/')
    }

    return (
        <form className="auth-card" onSubmit={handleSubmit}>
            <h1 className="page-title">로그인</h1>
            {signedUpEmail && <p className="notice">가입이 완료됐어요. 로그인해 주세요.</p>}
            <label className="field">
                <span>이메일</span>
                <input
                    type="email"
                    required
                    autoComplete="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                />
            </label>
            <label className="field">
                <span>비밀번호</span>
                <input
                    type="password"
                    required
                    autoComplete="current-password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                />
            </label>
            <button type="submit" className="btn btn-primary">로그인</button>
            {message && <p className="auth-message is-error">{message}</p>}
            <p className="auth-switch">
                계정이 없나요? <Link to="/signup">회원가입</Link>
            </p>
        </form>
    )
}

export default LoginPage