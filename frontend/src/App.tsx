import { Routes, Route } from 'react-router-dom';
import Layout from './components/Layout';
import Dashboard from './pages/Dashboard';
import Analyze from './pages/Analyze';
import Results from './pages/Results';

function App() {
    return (
        <Routes>
            <Route path="/" element={<Layout />}>
                <Route index element={<Dashboard />} />
                <Route path="analyze" element={<Analyze />} />
                <Route path="results" element={<Results />} />
            </Route>
        </Routes>
    );
}

export default App;