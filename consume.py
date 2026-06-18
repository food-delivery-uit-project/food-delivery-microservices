from kafka import KafkaConsumer
consumer = KafkaConsumer('payment-events', bootstrap_servers='localhost:9092', auto_offset_reset='earliest', consumer_timeout_ms=5000)
for msg in consumer:
    print(msg.value.decode('utf-8'))
