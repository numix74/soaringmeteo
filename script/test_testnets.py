import os
from dotenv import load_dotenv
import ccxt

load_dotenv()

api_key = os.getenv("BINANCE_API_KEY")
api_secret = os.getenv("BINANCE_API_SECRET")

print("=" * 70)
print("TEST TESTNETS")
print("=" * 70)

if not api_key or not api_secret:
    print("❌ Clés non trouvées dans .env")
    exit(1)

print(f"Clés trouvées : {api_key[:10]}...")

# TEST FUTURES
print("\nTEST FUTURES TESTNET...")
try:
    futures = ccxt.binance({
        'apiKey': api_key,
        'secret': api_secret,
        'enableRateLimit': True,
        'options': {'defaultType': 'future'},
        'urls': {
            'api': {
                'public': 'https://testnet.binancefuture.com/fapi/v1',
                'private': 'https://testnet.binancefuture.com/fapi/v1'
            }
        }
    })
    
    balance = futures.fetch_balance()
    usdt = balance.get('USDT', {}).get('total', 0)
    print(f"✅ FUTURES OK - Balance: ${usdt:,.2f}")
    
except Exception as e:
    print(f"❌ FUTURES KO : {e}")

# TEST SPOT
print("\nTEST SPOT TESTNET...")
try:
    spot = ccxt.binance({
        'apiKey': api_key,
        'secret': api_secret,
        'enableRateLimit': True,
        'options': {'defaultType': 'spot'},
        'urls': {
            'api': {
                'public': 'https://testnet.binance.vision/api/v3',
                'private': 'https://testnet.binance.vision/api/v3'
            }
        }
    })
    
    balance = spot.fetch_balance()
    usdt = balance.get('USDT', {}).get('total', 0)
    print(f"✅ SPOT OK - Balance: ${usdt:,.2f}")
    
except Exception as e:
    print(f"❌ SPOT KO : {e}")
