import { useState } from 'react'
import heroImg from './assets/hero.png'
import reactLogo from './assets/react.svg'
import viteLogo from './assets/vite.svg'
import './App.css'

function App() {
  const title = 'Cineo'
  const price = 12000

  return (
    <div>
      <h1 className="title">{title}</h1>
      <p>티켓 가격: {price}원</p>
      <p>2장이면: {price * 2}원</p>
      <input />
    </div>
  )
}

export default App