import { useState } from 'react'
import { useNavigate } from 'react-router'

function LoginPage() {
    const [email, setEmail] = useState('')
    const [password, setPassword] = useState('')
    const [message, setMessage] = useState('')
    const navigate = useNavigate()

    async function handleSubmit(e) {
        e.preventDefault()

        let response
        try {
            response = await fetch('http://localhost:8082/api/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email, password }),
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
        localStorage.setItem('accessToken', data.accessToken)
        localStorage.setItem('refreshToken', data.refreshToken)
        navigate('/')
    }

    return (
        <form onSubmit={handleSubmit}>
            <h2>로그인</h2>
            <input
                type="email"
                required
                placeholder="이메일"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
            />
            <input
                type="password"
                required
                placeholder="비밀번호"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
            />
            <button type="submit">로그인</button>
            {message && <p>{message}</p>}
        </form>
    )
}

export default LoginPage