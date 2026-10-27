import { useState, useEffect } from 'react'
import { apiFetch } from '../../api/client'

// 서버 검증(ScreenRegisterRequest)과 같은 범위
const MAX_ROWS = 26
const MAX_COLS = 50

function inRange(value, max) {
    return Number.isInteger(value) && value >= 1 && value <= max
}

function AdminScreensPage() {
    const [screens, setScreens] = useState(null)
    const [error, setError] = useState(null)
    const [reloadKey, setReloadKey] = useState(0)

    const [name, setName] = useState('')
    const [rowCount, setRowCount] = useState('8')
    const [colCount, setColCount] = useState('12')
    const [submitting, setSubmitting] = useState(false)
    const [message, setMessage] = useState(null)

    useEffect(() => {
        async function load() {
            try {
                const response = await apiFetch('/api/admin/screens')
                if (!response.ok) {
                    setError('상영관 목록을 불러오지 못했어요')
                    return
                }
                setScreens(await response.json())
            } catch {
                setError('서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.')
            }
        }
        load()
    }, [reloadKey])

    const rows = Number(rowCount)
    const cols = Number(colCount)
    const validSize = inRange(rows, MAX_ROWS) && inRange(cols, MAX_COLS)
    // A, B, C ... 행 이름. 서버도 같은 방식으로 좌석을 만든다
    const rowLabels = validSize ? Array.from({ length: rows }, (_, i) => String.fromCharCode(65 + i)) : []

    async function handleSubmit(e) {
        e.preventDefault()
        setSubmitting(true)
        setMessage(null)
        let response
        try {
            response = await apiFetch('/api/admin/screens', {
                method: 'POST',
                body: { name: name.trim(), rowCount: rows, colCount: cols },
            })
        } catch {
            setMessage({ error: true, text: '서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.' })
            return
        } finally {
            setSubmitting(false)
        }
        if (!response.ok) {
            setMessage({ error: true, text: await response.text() })
            return
        }
        setMessage({ error: false, text: `${name.trim()}을(를) 등록했어요. 좌석 ${rows * cols}석` })
        setName('')
        setReloadKey(key => key + 1)
    }

    return (
        <div className="admin-columns">
            <div>
                <h2 className="section-title">상영관 등록</h2>
                <form className="admin-form" onSubmit={handleSubmit}>
                    <label className="field">
                        <span>이름</span>
                        <input
                            required
                            maxLength={50}
                            placeholder="예: 4관"
                            value={name}
                            onChange={(e) => setName(e.target.value)}
                        />
                    </label>
                    <div className="admin-form-row">
                        <label className="field">
                            <span>행 수 <small>1~{MAX_ROWS}</small></span>
                            <input
                                type="number"
                                required
                                min={1}
                                max={MAX_ROWS}
                                value={rowCount}
                                onChange={(e) => setRowCount(e.target.value)}
                            />
                        </label>
                        <label className="field">
                            <span>열 수 <small>1~{MAX_COLS}</small></span>
                            <input
                                type="number"
                                required
                                min={1}
                                max={MAX_COLS}
                                value={colCount}
                                onChange={(e) => setColCount(e.target.value)}
                            />
                        </label>
                    </div>
                    <button type="submit" className="btn btn-primary" disabled={submitting || !validSize}>
                        {submitting ? '등록하는 중...' : '등록'}
                    </button>
                    {message && <p className={message.error ? 'auth-message is-error' : 'auth-message'}>{message.text}</p>}
                </form>

                <h2 className="section-title">상영관 목록</h2>
                {error && <p className="notice notice-error">{error}</p>}
                {!error && screens === null && <p className="notice">불러오는 중...</p>}
                {screens !== null && screens.length === 0 && <p className="notice">등록된 상영관이 없어요.</p>}
                {screens !== null && screens.length > 0 && (
                    <ul className="admin-list">
                        {screens.map(screen => <li key={screen.id}>{screen.name}</li>)}
                    </ul>
                )}
            </div>

            <div>
                <h2 className="section-title">좌석 미리보기 {validSize && <small className="admin-muted">{rows * cols}석</small>}</h2>
                {validSize ? (
                    <div className="screen-preview">
                        <div className="screen-preview-screen">SCREEN</div>
                        {rowLabels.map(label => (
                            <div key={label} className="screen-preview-row">
                                <span className="screen-preview-label">{label}</span>
                                {Array.from({ length: cols }, (_, i) => (
                                    <span key={i} className="screen-preview-seat" title={`${label}${i + 1}`} />
                                ))}
                            </div>
                        ))}
                    </div>
                ) : (
                    <p className="notice">행은 1~{MAX_ROWS}, 열은 1~{MAX_COLS} 사이로 입력해 주세요.</p>
                )}
            </div>
        </div>
    )
}

export default AdminScreensPage
