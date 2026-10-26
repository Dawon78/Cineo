import { useState } from 'react'
import heroImg from './assets/hero.png'
import reactLogo from './assets/react.svg'
import viteLogo from './assets/vite.svg'
import './App.css'
import MovieCard from './components/MovieCard'
import TicketCounter from './components/TicketCounter'

function App() {
  const title = 'Cineo'
  const price = 12000
  const movies = [
    { id: 1, title: '인셉션', runtime: 148 },
    { id: 2, title: '인터스텔라', runtime: 169 },
    { id: 3, title: '듄', runtime: 155 },
  ]

  return (
    <div>
      <h1 className="title">{title}</h1>
      <p>티켓 가격: {price}원</p>
      <p>2장이면: {price * 2}원</p>
      <input />
      {movies.map(movie => (
        <MovieCard key={movie.id} title={movie.title} runtime={movie.runtime} />
      ))}
      <TicketCounter />
    </div>
  )
}

export default App