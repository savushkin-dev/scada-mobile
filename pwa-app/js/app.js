// PWA App Logic
console.log('SCADA Mobile PWA initialized');

// Service Worker Registration
if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
        navigator.serviceWorker.register('/service-worker.js')
            .then((registration) => {
                console.log('Service Worker registered successfully:', registration.scope);
                showStatus('Service Worker активирован', 'success');
            })
            .catch((error) => {
                console.error('Service Worker registration failed:', error);
                showStatus('Ошибка активации Service Worker', 'error');
            });
    });
}

// Online/Offline Status
function updateOnlineStatus() {
    const statusIndicator = document.getElementById('onlineStatus');
    const statusText = document.getElementById('statusText');
    
    if (navigator.onLine) {
        statusIndicator.classList.add('online');
        statusIndicator.classList.remove('offline');
        statusText.textContent = 'Онлайн';
    } else {
        statusIndicator.classList.remove('online');
        statusIndicator.classList.add('offline');
        statusText.textContent = 'Оффлайн';
    }
}

// Listen for online/offline events
window.addEventListener('online', updateOnlineStatus);
window.addEventListener('offline', updateOnlineStatus);

// Initialize status on load
window.addEventListener('load', updateOnlineStatus);

// Action Button Handler
const actionButton = document.getElementById('actionButton');
let clickCount = 0;

actionButton.addEventListener('click', () => {
    clickCount++;
    
    const messages = [
        'Система инициализирована',
        'Подключение к SCADA...',
        'Мониторинг активирован',
        'Все системы работают нормально',
        'Данные синхронизированы'
    ];
    
    const message = messages[clickCount % messages.length];
    showStatus(message, 'success');
    
    // Add some visual feedback
    actionButton.style.transform = 'scale(0.95)';
    setTimeout(() => {
        actionButton.style.transform = '';
    }, 150);
});

// Status Message Display
function showStatus(message, type = 'info') {
    const statusElement = document.getElementById('status');
    statusElement.textContent = message;
    statusElement.className = `status-message ${type}`;
    
    // Auto-hide after 3 seconds with fade out
    setTimeout(() => {
        statusElement.style.opacity = '0';
        statusElement.style.transition = 'opacity 0.3s ease';
        setTimeout(() => {
            statusElement.style.display = 'none';
            statusElement.style.opacity = '1';
        }, 300);
    }, 3000);
}

// PWA Install Prompt
let deferredPrompt;
let installButtonListenerAdded = false;

window.addEventListener('beforeinstallprompt', (e) => {
    console.log('beforeinstallprompt event fired');
    // Prevent the mini-infobar from appearing on mobile
    e.preventDefault();
    // Stash the event so it can be triggered later
    deferredPrompt = e;
    // Show install prompt
    showInstallPrompt();
});

function showInstallPrompt() {
    const installPrompt = document.getElementById('installPrompt');
    const installButton = document.getElementById('installButton');
    
    installPrompt.style.display = 'block';
    
    // Only add event listener once
    if (!installButtonListenerAdded) {
        installButtonListenerAdded = true;
        installButton.addEventListener('click', async () => {
            if (!deferredPrompt) {
                return;
            }
            
            // Show the install prompt
            deferredPrompt.prompt();
            
            // Wait for the user to respond to the prompt
            const { outcome } = await deferredPrompt.userChoice;
            console.log(`User response to the install prompt: ${outcome}`);
            
            if (outcome === 'accepted') {
                showStatus('Приложение установлено!', 'success');
            } else {
                showStatus('Установка отменена', 'info');
            }
            
            // Clear the deferred prompt
            deferredPrompt = null;
            installPrompt.style.display = 'none';
        });
    }
}

// Detect if app is running in standalone mode
window.addEventListener('DOMContentLoaded', () => {
    const isStandalone = window.matchMedia('(display-mode: standalone)').matches 
                      || window.navigator.standalone 
                      || document.referrer.includes('android-app://');
    
    if (isStandalone) {
        console.log('App is running in standalone mode');
        showStatus('Приложение запущено в автономном режиме', 'success');
    }
});

// Handle app visibility changes
document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') {
        console.log('App is visible');
        updateOnlineStatus();
    }
});

// Log app version
const APP_VERSION = '1.0.0';
console.log(`SCADA Mobile PWA v${APP_VERSION}`);
