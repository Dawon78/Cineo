import { useState, useEffect, useSyncExternalStore } from 'react'
import { NavLink, Navigate, Outlet, useLocation } from 'react-router'
import { apiFetch } from '../../api/client'
import { isLoggedIn, subscribeAuth } from '../../auth'

function AdminLayout() {
    const location = useLocation()
    const loggedIn = useSyncExternalStore(subscribeAuth, isLoggedIn)
    const [role, setRole] = useState(null)
    const [error, setError] = useState(null)

    // 권한 확인은 여기서 한 번만 한다. 화면은 편의용으로 막고, 실제로 막는 건 서버의 hasRole('ADMIN')이다
    useEffect(() => {
        if (!loggedIn) {
            return
        }
        async function load() {
            try {
                const response = await apiFetch('/api/members/me')
                if (!response.ok) {
                    // 401이면 apiFetch가 토큰을 지우고, loggedIn이 false가 되면서 로그인 화면으로 간다
                    if (response.status !== 401) {
                        setError('내 정보를 불러오지 못했어요')
                    }
                    return
                }
                const member = await response.json()
                setRole(member.role)
            } catch {
                setError('서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.')
            }
        }
        load()
    }, [loggedIn])

    // 로그인이 안 됐거나 쓰는 중에 토큰이 만료되면, 로그인 후 보던 관리자 화면으로 돌아오게 한다
    if (!loggedIn) {
        return <Navigate to="/login" state={{ from: location.pathname }} replace />
    }

    if (error) {
        return <p className="notice notice-error">{error}</p>
    }

    if (role === null) {
        return <p className="notice">권한을 확인하는 중...</p>
    }

    if (role !== 'ADMIN') {
        return <p className="notice">관리자만 볼 수 있는 화면이에요.</p>
    }

    return (
        <section>
            <h1 className="page-title">관리자</h1>
            <nav className="admin-tabs">
                <NavLink to="/admin/movies">영화</NavLink>
                <NavLink to="/admin/screens">상영관</NavLink>
                <NavLink to="/admin/showtimes">상영시간</NavLink>
            </nav>
            <Outlet />
        </section>
    )
}

export default AdminLayout
