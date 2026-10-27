import { useState } from 'react'
import { useNavigate, Link } from 'react-router'

const MIN_PASSWORD_LENGTH = 8

function SignupPage() {
    const [email, setEmail] = useState('')
    const [password, setPassword] = useState('')
    const [passwordConfirm, setPasswordConfirm] = useState('')
    const [name, setName] = useState('')
    const [phone, setPhone] = useState('')
    const [message, setMessage] = useState('')
    const [submitting, setSubmitting] = useState(false)
    const navigate = useNavigate()

    async function handleSubmit(e) {
        e.preventDefault()

        if (password !== passwordConfirm) {
            setMessage('비밀번호가 서로 달라요.')
            return
        }

        setSubmitting(true)
        setMessage('')
        let response
        try {
            response = await fetch('http://localhost:8082/api/auth/signup', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email, password, name, phone }),
            })
        } catch {
            setMessage('서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.')
            return
        } finally {
            setSubmitting(false)
        }

        if (!response.ok) {
            setMessage(await response.text())
            return
        }

        navigate('/login', { state: { signedUpEmail: email } })
    }

    return (
        <form className="auth-card" onSubmit={handleSubmit}>
            <h1 className="page-title">회원가입</h1>
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
                <span>비밀번호 <small>{MIN_PASSWORD_LENGTH}자 이상</small></span>
                <input
                    type="password"
                    required
                    minLength={MIN_PASSWORD_LENGTH}
                    autoComplete="new-password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                />
            </label>
            <label className="field">
                <span>비밀번호 확인</span>
                <input
                    type="password"
                    required
                    autoComplete="new-password"
                    value={passwordConfirm}
                    onChange={(e) => setPasswordConfirm(e.target.value)}
                />
            </label>
            <label className="field">
                <span>이름 <small>선택</small></span>
                <input
                    type="text"
                    maxLength={50}
                    autoComplete="name"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                />
            </label>
            <label className="field">
                <span>전화번호 <small>선택</small></span>
                <input
                    type="tel"
                    maxLength={20}
                    autoComplete="tel"
                    value={phone}
                    onChange={(e) => setPhone(e.target.value)}
                />
            </label>
            <button type="submit" className="btn btn-primary" disabled={submitting}>
                {submitting ? '가입 중...' : '가입하기'}
            </button>
            {message && <p className="auth-message is-error">{message}</p>}
            <p className="auth-switch">
                이미 계정이 있나요? <Link to="/login">로그인</Link>
            </p>
        </form>
    )
}

export default SignupPage
