import { useState, useEffect, useSyncExternalStore } from 'react'
import { Routes, Route, Link, NavLink, Navigate, useNavigate } from 'react-router'
import './App.css'
import MovieListPage from './pages/MovieListPage'
import LoginPage from './pages/LoginPage'
import MovieDetailPage from './pages/MovieDetailPage'
import SeatPage from './pages/SeatPage'
import PaymentPage from './pages/PaymentPage'
import SignupPage from './pages/SignupPage'
import MyPage from './pages/MyPage'
import ReservationDetailPage from './pages/ReservationDetailPage'
import AdminLayout from './pages/admin/AdminLayout'
import AdminMoviesPage from './pages/admin/AdminMoviesPage'
import AdminScreensPage from './pages/admin/AdminScreensPage'
import AdminShowtimesPage from './pages/admin/AdminShowtimesPage'
import { isLoggedIn, clearTokens, subscribeAuth } from './auth'
import { apiFetch } from './api/client'

function App() {
  const navigate = useNavigate()
  const loggedIn = useSyncExternalStore(subscribeAuth, isLoggedIn)
  const [role, setRole] = useState(null)

  // 로그인 상태가 바뀔 때마다 권한을 다시 확인한다. 메뉴를 숨기는 건 편의용이고, 실제로 막는 건 서버다
  useEffect(() => {
    if (!loggedIn) {
      return
    }
    let ignore = false
    async function loadRole() {
      try {
        const response = await apiFetch('/api/members/me')
        if (!ignore && response.ok) {
          const member = await response.json()
          setRole(member.role)
        }
      } catch {
        // 서버가 꺼져 있으면 관리자 메뉴만 안 보이고 나머지는 그대로 쓴다
      }
    }
    loadRole()
    return () => {
      ignore = true
    }
  }, [loggedIn])

  const isAdmin = loggedIn && role === 'ADMIN'

  async function handleLogout() {
    try {
      await apiFetch('/api/auth/logout', { method: 'POST' })
    } catch {
      // 서버가 꺼져 있어도 브라우저의 토큰은 지워서 로그아웃 상태로 만든다
    }
    clearTokens()
    setRole(null)
    navigate('/')
  }

  return (
    <>
      <header className="site-header">
        <Link to="/" className="logo">Cineo</Link>
        <nav className="site-nav">
          <NavLink to="/" end>영화</NavLink>
          {loggedIn ? (
            <>
              <NavLink to="/mypage">마이페이지</NavLink>
              {isAdmin && <NavLink to="/admin">관리자</NavLink>}
              <button type="button" className="nav-button" onClick={handleLogout}>로그아웃</button>
            </>
          ) : (
            <>
              <NavLink to="/login">로그인</NavLink>
              <NavLink to="/signup">회원가입</NavLink>
            </>
          )}
        </nav>
      </header>

      <main className="page">
        <Routes>
          <Route path="/" element={<MovieListPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/signup" element={<SignupPage />} />
          <Route path="/mypage" element={<MyPage />} />
          <Route path="/movies/:movieId" element={<MovieDetailPage />} />
          <Route path="/showtimes/:showtimeId/seats" element={<SeatPage />} />
          <Route path="/payment" element={<PaymentPage />} />
          <Route path="/reservations/:reservationId" element={<ReservationDetailPage />} />
          <Route path="/admin" element={<AdminLayout />}>
            <Route index element={<Navigate to="movies" replace />} />
            <Route path="movies" element={<AdminMoviesPage />} />
            <Route path="screens" element={<AdminScreensPage />} />
            <Route path="showtimes" element={<AdminShowtimesPage />} />
          </Route>
        </Routes>
      </main>
    </>
  )
}

export default App
