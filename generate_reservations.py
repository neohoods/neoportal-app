#!/usr/bin/env python3
"""
Script pour générer des réservations réalistes avec des taux d'occupation spécifiques
entre le 1er janvier 2024 et le 1er janvier 2026.
"""

import random
import uuid
from datetime import datetime, timedelta, date
from typing import List, Tuple, Dict
import json

# Configuration des espaces et leurs taux d'occupation cibles
SPACES_CONFIG = {
    # Parkings (10 places) - taux d'occupation entre 20% et 70%
    '550e8400-e29b-41d4-a716-446655440101': {'type': 'PARKING', 'occupancy_rate': 0.45, 'name': 'Place de parking N°7'},
    '550e8400-e29b-41d4-a716-446655440102': {'type': 'PARKING', 'occupancy_rate': 0.35, 'name': 'Place de parking N°23'},
    '550e8400-e29b-41d4-a716-446655440103': {'type': 'PARKING', 'occupancy_rate': 0.60, 'name': 'Place de parking N°45'},
    '550e8400-e29b-41d4-a716-446655440104': {'type': 'PARKING', 'occupancy_rate': 0.25, 'name': 'Place de parking N°67'},
    '550e8400-e29b-41d4-a716-446655440105': {'type': 'PARKING', 'occupancy_rate': 0.50, 'name': 'Place de parking N°89'},
    '550e8400-e29b-41d4-a716-446655440106': {'type': 'PARKING', 'occupancy_rate': 0.40, 'name': 'Place de parking N°102'},
    '550e8400-e29b-41d4-a716-446655440107': {'type': 'PARKING', 'occupancy_rate': 0.30, 'name': 'Place de parking N°34'},
    '550e8400-e29b-41d4-a716-446655440108': {'type': 'PARKING', 'occupancy_rate': 0.55, 'name': 'Place de parking N°56'},
    '550e8400-e29b-41d4-a716-446655440109': {'type': 'PARKING', 'occupancy_rate': 0.65, 'name': 'Place de parking N°78'},
    '550e8400-e29b-41d4-a716-446655440110': {'type': 'PARKING', 'occupancy_rate': 0.20, 'name': 'Place de parking N°91'},
    
    # Chambre d'hôte - 35%
    '550e8400-e29b-41d4-a716-446655440111': {'type': 'GUEST_ROOM', 'occupancy_rate': 0.35, 'name': 'Chambre d\'hôte'},
    
    # Salle commune - 15%
    '550e8400-e29b-41d4-a716-446655440112': {'type': 'COMMON_ROOM', 'occupancy_rate': 0.15, 'name': 'Salle commune'},
    
    # Coworking A - 45%
    '550e8400-e29b-41d4-a716-446655440113': {'type': 'COWORKING', 'occupancy_rate': 0.45, 'name': 'Bureau coworking A'},
    
    # Coworking B - 20%
    '550e8400-e29b-41d4-a716-446655440114': {'type': 'COWORKING', 'occupancy_rate': 0.20, 'name': 'Bureau coworking B'},
}

# Utilisateurs disponibles
USER_IDS = [
    '8cf28343-7b32-4365-8c04-305f342a2cee',  # john_doe
    'c4e8c95e-682b-440d-b6d5-6297f0d13633',  # jane_smith
    'c5c180f1-bd25-443e-8f8a-924ddf13f971',  # alice_jones
    '593e726d-14b1-477e-967c-72bec8478a45',  # bob_brown
    '331f5b7e-3acd-4e91-b64d-9fee522b5f31',  # emily_davis
    '56f91978-04c8-4142-93e4-706c5d23dacf',  # michael_wilson
    'd4562dfd-2d98-4db0-9937-33fccd90599a',  # olivia_martinez
    'a42d1145-cb3a-4088-a9f8-eba1310a9e80',  # david_lee
    '26be1301-4121-4497-80d1-e448fdef0532',  # sophia_garcia
    'f336a25e-7bed-4f44-a360-f758aecd7d09',  # charlie_rodriguez
    '1968032b-4a3f-4044-bbf3-947b0c96f7a0',  # ava_walker
    'ce5578e6-87cf-44a0-877e-87c264dd281c',  # noah_turner
    'a668f324-debb-4cf0-a543-10a8ce7ed8e2',  # mia_hall
    'f71c870e-9daa-4991-accd-61f3c3c14fa2',  # demo
    'ff25a41d-a417-416c-9feb-dd61a7fcb2d6',  # qcastel
]

# Statuts de réservation avec probabilités
RESERVATION_STATUSES = [
    ('CONFIRMED', 0.70),
    ('PENDING_PAYMENT', 0.15),
    ('CANCELLED', 0.10),
    ('COMPLETED', 0.05),
]

# Statuts de paiement avec probabilités
PAYMENT_STATUSES = [
    ('SUCCEEDED', 0.75),
    ('PENDING', 0.15),
    ('FAILED', 0.05),
    ('CANCELLED', 0.05),
]

def generate_uuid():
    """Génère un UUID v4"""
    return str(uuid.uuid4())

def get_random_user():
    """Retourne un utilisateur aléatoire"""
    return random.choice(USER_IDS)

def get_random_status(statuses_with_prob):
    """Sélectionne un statut basé sur les probabilités"""
    rand = random.random()
    cumulative = 0
    for status, prob in statuses_with_prob:
        cumulative += prob
        if rand <= cumulative:
            return status
    return statuses_with_prob[0][0]

def get_random_date_in_range(start_date, end_date):
    """Génère une date aléatoire dans la plage donnée"""
    time_between = end_date - start_date
    days_between = time_between.days
    random_days = random.randrange(days_between)
    return start_date + timedelta(days=random_days)

def calculate_duration(space_type, space_id):
    """Calcule la durée de réservation basée sur le type d'espace"""
    if space_type == 'PARKING':
        # Parking: 1-15 jours
        return random.randint(1, 15)
    elif space_type == 'GUEST_ROOM':
        # Chambre d'hôte: 2-7 jours
        return random.randint(2, 7)
    elif space_type == 'COMMON_ROOM':
        # Salle commune: 1 jour
        return 1
    elif space_type == 'COWORKING':
        # Coworking: 1-5 jours
        return random.randint(1, 5)
    return 1

def calculate_price(space_type, duration):
    """Calcule le prix basé sur le type d'espace et la durée"""
    if space_type == 'PARKING':
        return 0.00  # Gratuit
    elif space_type == 'GUEST_ROOM':
        return round(70.00 * duration, 2)
    elif space_type == 'COMMON_ROOM':
        return round(20.00 * duration, 2)
    elif space_type == 'COWORKING':
        return round(8.00 * duration, 2)
    return 0.00

def generate_reservations_for_space(space_id, space_config, start_date, end_date):
    """Génère les réservations pour un espace donné avec le taux d'occupation cible"""
    space_type = space_config['type']
    target_occupancy = space_config['occupancy_rate']
    
    # Calculer le nombre total de jours dans la période
    total_days = (end_date - start_date).days
    
    # Calculer le nombre de jours occupés cibles
    target_occupied_days = int(total_days * target_occupancy)
    
    # Générer des réservations pour atteindre le taux d'occupation
    reservations = []
    occupied_days = set()
    
    # Générer des réservations jusqu'à atteindre le taux d'occupation
    while len(occupied_days) < target_occupied_days:
        # Choisir une date de début aléatoire
        start_reservation = get_random_date_in_range(start_date, end_date)
        duration = calculate_duration(space_type, space_id)
        end_reservation = start_reservation + timedelta(days=duration)
        
        # Vérifier que la réservation est dans la période
        if end_reservation > end_date:
            end_reservation = end_date
            duration = (end_reservation - start_reservation).days
        
        if duration <= 0:
            continue
            
        # Vérifier les conflits (simplifié - on évite juste les chevauchements majeurs)
        reservation_days = set()
        current_date = start_reservation
        while current_date < end_reservation:
            reservation_days.add(current_date)
            current_date += timedelta(days=1)
        
        # Si trop de conflits, on passe
        if len(occupied_days.intersection(reservation_days)) > duration * 0.3:
            continue
            
        # Créer la réservation
        reservation_id = generate_uuid()
        user_id = get_random_user()
        status = get_random_status(RESERVATION_STATUSES)
        payment_status = get_random_status(PAYMENT_STATUSES)
        price = calculate_price(space_type, duration)
        
        # Générer des IDs Stripe si le paiement a réussi
        stripe_payment_intent_id = None
        stripe_session_id = None
        if payment_status == 'SUCCEEDED':
            stripe_payment_intent_id = f'pi_{random.randint(1000000000, 9999999999)}'
            stripe_session_id = f'cs_{random.randint(1000000000, 9999999999)}'
        
        # Date de création (avant la réservation)
        created_at = start_reservation - timedelta(days=random.randint(1, 30))
        
        reservation = {
            'id': reservation_id,
            'space_id': space_id,
            'user_id': user_id,
            'start_date': start_reservation.strftime('%Y-%m-%d'),
            'end_date': end_reservation.strftime('%Y-%m-%d'),
            'status': status,
            'total_price': price,
            'stripe_payment_intent_id': stripe_payment_intent_id,
            'stripe_session_id': stripe_session_id,
            'payment_status': payment_status,
            'created_at': created_at.strftime('%Y-%m-%d %H:%M:%S+00'),
            'updated_at': created_at.strftime('%Y-%m-%d %H:%M:%S+00')
        }
        
        reservations.append(reservation)
        occupied_days.update(reservation_days)
        
        # Limite de sécurité pour éviter les boucles infinies
        if len(reservations) > total_days * 2:
            break
    
    return reservations

def generate_access_codes(reservations):
    """Génère les codes d'accès pour les réservations"""
    access_codes = []
    digital_lock_ids = [
        '550e8400-e29b-41d4-a716-446655440001',
        '550e8400-e29b-41d4-a716-446655440002',
        '550e8400-e29b-41d4-a716-446655440003',
        '550e8400-e29b-41d4-a716-446655440004',
    ]
    
    for reservation in reservations:
        if reservation['status'] in ['CONFIRMED', 'COMPLETED']:
            access_code_id = generate_uuid()
            code = str(random.randint(100000, 999999))
            
            # Date d'expiration (après la fin de la réservation)
            end_date = datetime.strptime(reservation['end_date'], '%Y-%m-%d')
            expires_at = end_date + timedelta(days=1)
            
            access_code = {
                'id': access_code_id,
                'reservation_id': reservation['id'],
                'code': code,
                'expires_at': expires_at.strftime('%Y-%m-%d %H:%M:%S+00'),
                'digital_lock_id': random.choice(digital_lock_ids),
                'digital_lock_code_id': f'lock-code-{random.randint(1000, 9999)}',
                'is_active': True,
                'created_at': reservation['created_at'],
                'updated_at': reservation['updated_at']
            }
            access_codes.append(access_code)
    
    return access_codes

def generate_feedback(reservations):
    """Génère des feedbacks pour certaines réservations"""
    feedbacks = []
    
    for reservation in reservations:
        if reservation['status'] == 'COMPLETED' and random.random() < 0.6:  # 60% de feedback
            feedback_id = generate_uuid()
            rating = random.randint(3, 5)  # Ratings positifs
            cleanliness = random.randint(3, 5)
            communication = random.randint(3, 5)
            value = random.randint(3, 5)
            
            comments = [
                "Excellent experience! The space was clean and well-maintained.",
                "Great space, would book again!",
                "Perfect for our needs, very satisfied.",
                "Clean and functional space, highly recommended.",
                "Good value for money, will definitely use again.",
                "Space was exactly as described, very happy.",
                "Easy booking process and great space.",
                "Clean, quiet, and well-equipped space.",
                "Excellent service and facilities.",
                "Perfect location and great amenities."
            ]
            
            end_date = datetime.strptime(reservation['end_date'], '%Y-%m-%d')
            submitted_at = end_date + timedelta(days=random.randint(1, 7))
            
            feedback = {
                'id': feedback_id,
                'reservation_id': reservation['id'],
                'user_id': reservation['user_id'],
                'rating': rating,
                'comment': random.choice(comments),
                'cleanliness': cleanliness,
                'communication': communication,
                'value': value,
                'submitted_at': submitted_at.strftime('%Y-%m-%d %H:%M:%S+00')
            }
            feedbacks.append(feedback)
    
    return feedbacks

def main():
    """Fonction principale"""
    print("Génération des réservations...")
    
    # Période: 1er janvier 2024 au 1er janvier 2026
    start_date = date(2024, 1, 1)
    end_date = date(2026, 1, 1)
    
    all_reservations = []
    all_access_codes = []
    all_feedbacks = []
    
    # Générer les réservations pour chaque espace
    for space_id, space_config in SPACES_CONFIG.items():
        print(f"Génération pour {space_config['name']} (taux: {space_config['occupancy_rate']*100:.1f}%)")
        reservations = generate_reservations_for_space(space_id, space_config, start_date, end_date)
        all_reservations.extend(reservations)
        
        # Générer les codes d'accès
        access_codes = generate_access_codes(reservations)
        all_access_codes.extend(access_codes)
        
        # Générer les feedbacks
        feedbacks = generate_feedback(reservations)
        all_feedbacks.extend(feedbacks)
    
    print(f"Total réservations générées: {len(all_reservations)}")
    print(f"Total codes d'accès générés: {len(all_access_codes)}")
    print(f"Total feedbacks générés: {len(all_feedbacks)}")
    
    # Générer le SQL
    generate_sql(all_reservations, all_access_codes, all_feedbacks)

def generate_sql(reservations, access_codes, feedbacks):
    """Génère le SQL pour les réservations"""
    
    # SQL pour les réservations
    reservations_sql = "-- Sample Reservations (generated)\n"
    reservations_sql += "INSERT INTO reservations (id, space_id, user_id, start_date, end_date, status, total_price, stripe_payment_intent_id, stripe_session_id, payment_status, created_at, updated_at) VALUES\n"
    
    reservation_values = []
    for res in reservations:
        stripe_pi = f"'{res['stripe_payment_intent_id']}'" if res['stripe_payment_intent_id'] else 'NULL'
        stripe_session = f"'{res['stripe_session_id']}'" if res['stripe_session_id'] else 'NULL'
        
        value = f"('{res['id']}', '{res['space_id']}', '{res['user_id']}', '{res['start_date']}', '{res['end_date']}', '{res['status']}', {res['total_price']:.2f}, {stripe_pi}, {stripe_session}, '{res['payment_status']}', '{res['created_at']}', '{res['updated_at']}')"
        reservation_values.append(value)
    
    reservations_sql += ",\n".join(reservation_values) + ";\n\n"
    
    # SQL pour les codes d'accès
    access_codes_sql = "-- Sample Access Codes (generated)\n"
    access_codes_sql += "INSERT INTO access_codes (id, reservation_id, code, expires_at, digital_lock_id, digital_lock_code_id, is_active, created_at, updated_at) VALUES\n"
    
    access_code_values = []
    for ac in access_codes:
        value = f"('{ac['id']}', '{ac['reservation_id']}', '{ac['code']}', '{ac['expires_at']}', '{ac['digital_lock_id']}', '{ac['digital_lock_code_id']}', {str(ac['is_active']).lower()}, '{ac['created_at']}', '{ac['updated_at']}')"
        access_code_values.append(value)
    
    access_codes_sql += ",\n".join(access_code_values) + ";\n\n"
    
    # SQL pour les feedbacks
    feedbacks_sql = "-- Sample Reservation Feedback (generated)\n"
    feedbacks_sql += "INSERT INTO reservation_feedback (id, reservation_id, user_id, rating, comment, cleanliness, communication, value, submitted_at) VALUES\n"
    
    feedback_values = []
    for fb in feedbacks:
        value = f"('{fb['id']}', '{fb['reservation_id']}', '{fb['user_id']}', {fb['rating']}, '{fb['comment']}', {fb['cleanliness']}, {fb['communication']}, {fb['value']}, '{fb['submitted_at']}')"
        feedback_values.append(value)
    
    feedbacks_sql += ",\n".join(feedback_values) + ";\n\n"
    
    # Écrire dans un fichier
    with open('/Users/qcastel/Development/GIT/github/neohoods/neoportal-app/generated_reservations.sql', 'w', encoding='utf-8') as f:
        f.write(reservations_sql)
        f.write(access_codes_sql)
        f.write(feedbacks_sql)
    
    print("SQL généré dans: generated_reservations.sql")

if __name__ == "__main__":
    main()
